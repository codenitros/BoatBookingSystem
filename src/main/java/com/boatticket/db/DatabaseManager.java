package com.boatticket.db;

import com.boatticket.config.ConfigManager;
import com.boatticket.model.BoatOwner;
import com.boatticket.model.Ticket;
import com.boatticket.model.TicketReport;
import com.boatticket.util.PdfReportManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mindrot.jbcrypt.BCrypt;

public class DatabaseManager {

  private static final String DB_DIR = ConfigManager.getAppDirectory();

  private static final String LEGACY_HONNAVAR_DB_DIR =
      System.getProperty("user.home") + File.separator + "BoatTicketSystem";

  private static final String DB_PATH = DB_DIR + File.separator + "tickets.db";

  private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

  private static final DateTimeFormatter TICKET_TIME_FORMAT =
      DateTimeFormatter.ofPattern("dd-MM-yyyy  HH:mm:ss");

  // Fallback boat capacity when a row's capacity cell is missing or
  // can't be parsed (e.g. blank, or garbage data in the source sheet).
  private static final int DEFAULT_BOAT_CAPACITY = 10;

  // Password used to lock the exported sheet against editing.
  // The operator only needs to view/print/share the file, never edit cells,
  // so this password is intentionally not exposed anywhere in the UI.
  private static final String EXPORT_PROTECTION_PASSWORD = "Doddamma";

  // Password required to OPEN the encrypted PDF ticket report at all.
  // Never shown anywhere in the UI — the operator can save/share the
  // file, but cannot open it without this password.
  private static final String REPORT_OPEN_PASSWORD = "Doddamma";

  private static DatabaseManager instance;

  private DatabaseManager() {
    try {
      new File(DB_DIR).mkdirs();
      migrateLegacyHonnavarData();
      copyBoatsCsvIfNeeded();
      initSchema();
      seedData();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialise database: " + e.getMessage(), e);
    }
  }

  public static synchronized DatabaseManager getInstance() {
    if (instance == null) {
      instance = new DatabaseManager();
    }

    return instance;
  }

  public Connection getConnection() throws SQLException {
    return DriverManager.getConnection(JDBC_URL);
  }

  private void initSchema() throws SQLException {
    try (Connection connection = getConnection();
        Statement statement = connection.createStatement()) {

      statement.executeUpdate(
          "CREATE TABLE IF NOT EXISTS users ("
              + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
              + "username TEXT NOT NULL UNIQUE,"
              + "password TEXT NOT NULL,"
              + "role TEXT NOT NULL DEFAULT 'agent'"
              + ")");

      statement.executeUpdate(
          "CREATE TABLE IF NOT EXISTS boats ("
              + "id INTEGER PRIMARY KEY,"
              + "driver_name TEXT NOT NULL,"
              + "boat_hr_number TEXT NOT NULL,"
              + "boat_hon_number TEXT NOT NULL,"
              + "jetty TEXT NOT NULL,"
              + "capacity INTEGER NOT NULL DEFAULT 10,"
              + "contact TEXT NOT NULL"
              + ")");

      statement.executeUpdate(
          "CREATE TABLE IF NOT EXISTS tickets ("
              + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
              + "ticket_id TEXT NOT NULL UNIQUE,"
              + "booking_time TEXT NOT NULL,"
              + "customer_name TEXT NOT NULL,"
              + "contact_number TEXT NOT NULL,"
              + "num_people INTEGER NOT NULL,"
              + "ride_duration_hour INTEGER NOT NULL DEFAULT 1,"
              + "life_jacket INTEGER NOT NULL DEFAULT 0,"
              + "life_jacket_count INTEGER NOT NULL DEFAULT 0,"
              + "parking INTEGER NOT NULL DEFAULT 0,"
              + "vehicle_type TEXT,"
              + "vehicle_number TEXT,"
              + "boat_id INTEGER REFERENCES boats(id),"
              + "boat_ride_fee INTEGER NOT NULL,"
              + "life_jacket_fee INTEGER NOT NULL DEFAULT 0,"
              + "parking_fee INTEGER NOT NULL DEFAULT 0,"
              + "total_fee INTEGER NOT NULL,"
              + "booked_by TEXT NOT NULL"
              + ")");
    }
  }

  private void seedData() throws SQLException {
    try (Connection connection = getConnection()) {
      try (Statement statement = connection.createStatement();
          ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM users")) {

        if (resultSet.getInt(1) == 0) {
          insertUser(connection, "admin", "admin@123", "admin");
          insertUser(connection, "agent1", "agent1@123", "agent");
          insertUser(connection, "agent2", "agent2@123", "agent");
          insertUser(connection, "agent3", "agent3@123", "agent");
        }
      }

      reloadBoats(connection);
    }
  }

  private void insertUser(Connection connection, String username, String plainPassword, String role)
      throws SQLException {
    String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());

    try (PreparedStatement statement =
        connection.prepareStatement("INSERT INTO users(username,password,role) VALUES(?,?,?)")) {
      statement.setString(1, username);
      statement.setString(2, hash);
      statement.setString(3, role);
      statement.executeUpdate();
    }
  }

  private void seedBoats(Connection connection) throws SQLException {
    Path csvPath = Paths.get(DB_DIR, "boats.csv");

    try (BufferedReader reader =
            Files.exists(csvPath)
                ? Files.newBufferedReader(csvPath)
                : new BufferedReader(
                    new InputStreamReader(
                        getClass().getClassLoader().getResourceAsStream("boats.csv")));
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO boats(id,driver_name,boat_hr_number,boat_hon_number,"
                    + "jetty,capacity,contact) VALUES(?,?,?,?,?,?,?)")) {

      String line;
      String currentJetty = "";
      int nextId = 1;
      int batched = 0;

      while ((line = reader.readLine()) != null) {
        // Keep trailing empty cells (e.g. "STAR BOATING,,,,,") so
        // we can reliably tell a jetty title row apart from a
        // real data row -- String.split(",") alone drops them.
        String[] parts = line.split(",", -1);

        if (isBlankRow(parts)) {
          continue; // spacer line between jetty sections
        }

        if (isColumnHeaderRow(parts)) {
          continue; // "Sl.no,Name,Hon no,HR no,Capacity,Mob no"
        }

        if (isJettyTitleRow(parts)) {
          currentJetty = parts[0].trim();
          continue;
        }

        // Otherwise this is a boat owner row:
        // Sl.no, Name, Hon no, HR no, Capacity, Mob no
        String driverName = cell(parts, 1);
        String honNumber = cell(parts, 2);
        String hrNumber = cell(parts, 3);
        String capacityRaw = cell(parts, 4);
        String contact = cell(parts, 5);

        if (driverName.isEmpty()) {
          continue; // nothing usable on this row
        }

        statement.setInt(1, nextId++);
        statement.setString(2, driverName);
        statement.setString(3, hrNumber);
        statement.setString(4, honNumber);
        statement.setString(5, currentJetty);
        statement.setInt(6, parseCapacity(capacityRaw));
        statement.setString(7, contact);
        statement.addBatch();
        batched++;
      }

      if (batched > 0) {
        statement.executeBatch();
      }
    } catch (IOException e) {
      throw new SQLException("Failed to read boats.csv", e);
    }
  }

  private static boolean isBlankRow(String[] parts) {
    for (String part : parts) {
      if (!part.trim().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static boolean isColumnHeaderRow(String[] parts) {
    String first = cell(parts, 0);
    return first.equalsIgnoreCase("Sl.no") || first.equalsIgnoreCase("Sl");
  }

  /**
   * A jetty/section title row looks like "STAR BOATING,,,,,": only the first cell has content, it's
   * not a numeric serial number, and it's not the "Sl.no" column-header row (already handled
   * separately).
   */
  private static boolean isJettyTitleRow(String[] parts) {
    String first = cell(parts, 0);

    if (first.isEmpty() || isNumeric(first)) {
      return false;
    }

    for (int i = 1; i < parts.length; i++) {
      if (!parts[i].trim().isEmpty()) {
        return false;
      }
    }

    return true;
  }

  private static boolean isNumeric(String text) {
    if (text.isEmpty()) {
      return false;
    }
    for (char c : text.toCharArray()) {
      if (!Character.isDigit(c)) {
        return false;
      }
    }
    return true;
  }

  private static String cell(String[] parts, int index) {
    return index < parts.length ? parts[index].trim() : "";
  }

  /**
   * Capacity in the source sheet is written like "4+2" (base seats +2 extra, e.g. for driver/crew).
   * Per the actual boat capacity we want to store and display, we ignore the "+2" part entirely and
   * just use the number before the "+". Falls back to {@link #DEFAULT_BOAT_CAPACITY} if the cell is
   * blank or unparseable.
   */
  private static int parseCapacity(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_BOAT_CAPACITY;
    }

    raw = raw.trim();

    if (raw.contains("+")) {
      raw = raw.substring(0, raw.indexOf('+')).trim();
    }

    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      return DEFAULT_BOAT_CAPACITY;
    }
  }

  private void reloadBoats(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM boats");
    }

    seedBoats(connection);
  }

  private void copyBoatsCsvIfNeeded() throws IOException {
    Path csvPath = Paths.get(DB_DIR, "boats.csv");

    if (!Files.exists(csvPath)) {
      try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("boats.csv")) {
        if (inputStream != null) {
          Files.copy(inputStream, csvPath);
        }
      }
    }
  }

  /**
   * Keeps existing Honnavar installations working after tenant data was separated into individual
   * folders. Kasarkod never reads the legacy shared directory, so tenant data remains isolated.
   */
  private static void migrateLegacyHonnavarData() throws IOException {
    if (!"honnavar".equals(ConfigManager.getPackagedTenantId())) {
      return;
    }

    Path legacyDirectory = Paths.get(LEGACY_HONNAVAR_DB_DIR);
    if (!Files.isDirectory(legacyDirectory)) {
      return;
    }

    copyLegacyFile(legacyDirectory, "tickets.db");
    copyLegacyFile(legacyDirectory, "boats.csv");
    copyLegacyFile(legacyDirectory, "Config.properties");
  }

  private static void copyLegacyFile(Path legacyDirectory, String filename) throws IOException {
    Path source = legacyDirectory.resolve(filename);
    Path destination = Paths.get(DB_DIR, filename);
    if (Files.isRegularFile(source) && !Files.exists(destination)) {
      Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
    }
  }

  public String authenticate(String username, String password) {
    try (Connection connection = getConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT password, role FROM users WHERE username=?")) {

      statement.setString(1, username);

      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next() && BCrypt.checkpw(password, resultSet.getString("password"))) {
          return resultSet.getString("role");
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return null;
  }

  public ResultSet getAllBoats() throws SQLException {
    Connection connection = getConnection();

    return connection
        .createStatement()
        .executeQuery(
            "SELECT id, driver_name, boat_hr_number, boat_hon_number, "
                + "jetty, capacity, contact FROM boats ORDER BY id");
  }

  public ResultSet getBoatById(int id) throws SQLException {
    Connection connection = getConnection();

    PreparedStatement statement = connection.prepareStatement("SELECT * FROM boats WHERE id=?");

    statement.setInt(1, id);

    return statement.executeQuery();
  }

  public void saveTicket(Ticket ticket, int boatId, String bookedBy) throws SQLException {
    try (Connection connection = getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO tickets(ticket_id,booking_time,customer_name,contact_number,"
                    + "num_people,life_jacket,life_jacket_count,parking,vehicle_type,"
                    + "vehicle_number,boat_id,boat_ride_fee,life_jacket_fee,parking_fee,"
                    + "total_fee,booked_by,ride_duration_hour) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {

      statement.setString(1, ticket.getTicketId());
      statement.setString(2, ticket.getFormattedBookingTime());
      statement.setString(3, ticket.getCustomerName());
      statement.setString(4, ticket.getContactNumber());
      statement.setInt(5, ticket.getNumberOfPeople());
      statement.setInt(6, ticket.isLifeJacketRequired() ? 1 : 0);
      statement.setInt(7, ticket.getLifeJacketCount());
      statement.setInt(8, ticket.isParkingRequired() ? 1 : 0);
      statement.setString(
          9, ticket.getVehicleType() != null ? ticket.getVehicleType().name() : null);
      statement.setString(10, ticket.getVehicleNumber());
      statement.setInt(11, boatId);
      statement.setInt(12, ticket.getBoatRideFee());
      statement.setInt(13, ticket.getLifeJacketFee());
      statement.setInt(14, ticket.getParkingFee());
      statement.setInt(15, ticket.getTotalFee());
      statement.setString(16, bookedBy);
      statement.setInt(17, ticket.getRideDurationInHour());
      statement.executeUpdate();
    }
  }

  /**
   * Exports tickets booked between fromDate and toDate (inclusive) to a read-only .xlsx file. The
   * sheet is protected (no password given to the operator) so the file can only be viewed, printed,
   * and shared — not edited.
   *
   * @return the exported File
   */
  public File exportToExcel(String destinationDir, LocalDate fromDate, LocalDate toDate)
      throws IOException, SQLException {

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

    String rangeLabel =
        fromDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"))
            + "_to_"
            + toDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"));

    Path filePath =
        Path.of(destinationDir, "tickets_export_" + rangeLabel + "_" + timestamp + ".xlsx");

    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Tickets");

      String[] headers = {
        "Ticket ID",
        "Booking Time",
        "Customer Name",
        "Contact Number",
        "Number of People",
        "Duration (Hours)",
        "Total Fee",
        "Driver Name",
        "Parking Fee"
      };

      Row headerRow = sheet.createRow(0);

      for (int index = 0; index < headers.length; index++) {
        Cell cell = headerRow.createCell(index);
        cell.setCellValue(headers[index]);
      }

      int rowNumber = 1;
      int totalParkingFee = 0;

      // Same rows (and same oldest-to-latest order) as the PDF report.
      List<TicketReport> reportRows = fetchTicketsInRange(fromDate, toDate);

      for (TicketReport report : reportRows) {
        Row row = sheet.createRow(rowNumber++);
        row.createCell(0).setCellValue(report.getTicketId());
        row.createCell(1).setCellValue(report.getBookingTime());
        row.createCell(2).setCellValue(report.getCustomerName());
        row.createCell(3).setCellValue(report.getContactNumber());
        row.createCell(4).setCellValue(report.getNumberOfPeople());
        row.createCell(5).setCellValue(report.getRideDurationInHour());
        row.createCell(6).setCellValue(report.getTotalFee());
        row.createCell(7).setCellValue(report.getDriverName());
        row.createCell(8).setCellValue(report.getParkingFee());

        totalParkingFee += report.getParkingFee();
      }

      Row totalRow = sheet.createRow(rowNumber + 1);
      totalRow.createCell(0).setCellValue("Total Money Collected at Counter:");
      totalRow.createCell(1).setCellValue(totalParkingFee);

      Row rangeRow = sheet.createRow(rowNumber + 2);
      rangeRow.createCell(0).setCellValue("Date Range:");
      rangeRow
          .createCell(1)
          .setCellValue(
              fromDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                  + "  to  "
                  + toDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));

      for (int index = 0; index < headers.length; index++) {
        sheet.autoSizeColumn(index);
      }

      // --- Lock the file down to read-only: viewable, printable, and
      // shareable, but not editable in Excel/LibreOffice/etc. ---
      sheet.protectSheet(EXPORT_PROTECTION_PASSWORD);

      File exportedFile = filePath.toFile();

      try (FileOutputStream outputStream = new FileOutputStream(exportedFile)) {
        workbook.write(outputStream);
      }

      return exportedFile;
    }
  }

  private LocalDate parseBookingDate(String bookingTimeText) {
    LocalDateTime bookingDateTime = parseBookingDateTime(bookingTimeText);
    return bookingDateTime == null ? null : bookingDateTime.toLocalDate();
  }

  // Converts the stored booking time text into a real date-time.
  // Returns null if the text is blank or not in the expected format.
  private LocalDateTime parseBookingDateTime(String bookingTimeText) {
    if (bookingTimeText == null || bookingTimeText.isBlank()) {
      return null;
    }

    try {
      return LocalDateTime.parse(bookingTimeText.trim(), TICKET_TIME_FORMAT);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Fetches all tickets whose booking date falls within fromDate..toDate (inclusive), flattened
   * into simple report rows. Shared by both the Excel and PDF report exporters.
   */
  private List<TicketReport> fetchTicketsInRange(LocalDate fromDate, LocalDate toDate)
      throws SQLException {

    List<TicketReport> rows = new ArrayList<>();

    try (Connection connection = getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT t.ticket_id, t.booking_time, t.customer_name, "
                    + "t.contact_number, t.num_people, t.ride_duration_hour, "
                    + "t.total_fee, b.driver_name, t.parking_fee "
                    + "FROM tickets t LEFT JOIN boats b ON t.boat_id = b.id");
        ResultSet resultSet = statement.executeQuery()) {

      while (resultSet.next()) {
        String bookingTimeText = resultSet.getString("booking_time");
        LocalDate bookingDate = parseBookingDate(bookingTimeText);

        if (bookingDate == null || bookingDate.isBefore(fromDate) || bookingDate.isAfter(toDate)) {
          continue;
        }

        rows.add(
            new TicketReport(
                resultSet.getString("ticket_id"),
                bookingTimeText,
                resultSet.getString("customer_name"),
                resultSet.getString("contact_number"),
                resultSet.getInt("num_people"),
                resultSet.getInt("ride_duration_hour"),
                resultSet.getInt("total_fee"),
                resultSet.getString("driver_name"),
                resultSet.getInt("parking_fee")));
      }
    }

    // booking_time is stored as text "dd-MM-yyyy  HH:mm:ss". Sorting that text in SQL
    // orders by DAY number first (30, 29, 18, 14 ...) and ignores the month, so the
    // report came out mixed up. Sort here by the real date-time instead: oldest first.
    rows.sort(
        Comparator.comparing(
            (TicketReport row) -> parseBookingDateTime(row.getBookingTime()),
            Comparator.nullsLast(Comparator.naturalOrder())));

    return rows;
  }

  /**
   * Exports tickets booked between fromDate and toDate (inclusive) to a genuinely
   * password-protected PDF report (real PDF encryption — the file cannot be opened in any PDF
   * reader without the password). The password is never shown anywhere in the UI; the operator can
   * still save the file wherever they like and share it with others, but cannot open it themselves
   * without the password.
   *
   * @return the exported File
   */
  public File exportToPdfReport(String destinationDir, LocalDate fromDate, LocalDate toDate)
      throws Exception {

    List<TicketReport> rows = fetchTicketsInRange(fromDate, toDate);

    int totalParkingFee = 0;

    for (TicketReport row : rows) {
      totalParkingFee += row.getParkingFee();
    }

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

    String rangeLabel =
        fromDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"))
            + "_to_"
            + toDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"));

    File destinationFile =
        new File(destinationDir, "tickets_report_" + rangeLabel + "_" + timestamp + ".pdf");

    PdfReportManager.generatePasswordProtectedReport(
        destinationFile, rows, fromDate, toDate, totalParkingFee, REPORT_OPEN_PASSWORD);

    return destinationFile;
  }

  public String getDbPath() {
    return DB_PATH;
  }

  public List<Ticket> getTodaysTickets() throws SQLException {
    List<Ticket> tickets = new ArrayList<>();

    String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

    String query =
        "SELECT "
            + "t.ticket_id, t.booking_time, t.customer_name, t.contact_number, "
            + "t.num_people, t.life_jacket, t.life_jacket_count, t.parking, "
            + "t.vehicle_type, t.vehicle_number, t.boat_ride_fee, "
            + "t.life_jacket_fee, t.parking_fee, t.total_fee, t.booked_by, "
            + "t.ride_duration_hour, "
            + "b.id AS boat_id, b.driver_name, b.boat_hr_number, "
            + "b.boat_hon_number, b.jetty, b.capacity, b.contact AS boat_contact "
            + "FROM tickets t "
            + "LEFT JOIN boats b ON t.boat_id = b.id "
            + "WHERE t.booking_time LIKE ? "
            + "ORDER BY t.booking_time DESC";

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(query)) {

      statement.setString(1, today + "%");

      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          tickets.add(mapTicket(resultSet));
        }
      }
    }

    return tickets;
  }

  private Ticket mapTicket(ResultSet resultSet) throws SQLException {
    LocalDateTime bookingTime =
        LocalDateTime.parse(resultSet.getString("booking_time"), TICKET_TIME_FORMAT);

    Ticket ticket = new Ticket(resultSet.getString("ticket_id"), bookingTime);

    ticket.setCustomerName(resultSet.getString("customer_name"));
    ticket.setContactNumber(resultSet.getString("contact_number"));
    ticket.setNumberOfPeople(resultSet.getInt("num_people"));
    ticket.setLifeJacketRequired(resultSet.getInt("life_jacket") == 1);
    ticket.setLifeJacketCount(resultSet.getInt("life_jacket_count"));
    ticket.setParkingRequired(resultSet.getInt("parking") == 1);
    ticket.setVehicleNumber(resultSet.getString("vehicle_number"));
    ticket.setBookedBy(resultSet.getString("booked_by"));
    ticket.setRideDurationInHour(resultSet.getInt("ride_duration_hour"));
    ticket.setStoredFees(
        resultSet.getInt("boat_ride_fee"),
        resultSet.getInt("life_jacket_fee"),
        resultSet.getInt("parking_fee"),
        resultSet.getInt("total_fee"));

    String vehicleType = resultSet.getString("vehicle_type");

    if (vehicleType != null && !vehicleType.isBlank()) {
      ticket.setVehicleType(Ticket.VehicleType.valueOf(vehicleType));
    }

    int boatId = resultSet.getInt("boat_id");

    if (!resultSet.wasNull()) {
      ticket.setBoatOwner(
          new BoatOwner(
              boatId,
              resultSet.getString("driver_name"),
              resultSet.getString("boat_hr_number"),
              resultSet.getString("boat_hon_number"),
              resultSet.getString("jetty"),
              resultSet.getInt("capacity"),
              resultSet.getString("boat_contact")));
    }

    return ticket;
  }
}
