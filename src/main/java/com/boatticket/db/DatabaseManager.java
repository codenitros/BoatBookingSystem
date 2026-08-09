package com.boatticket.db;

import com.boatticket.model.BoatOwner;
import com.boatticket.model.Ticket;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.List;

public class DatabaseManager {

    private static final String DB_DIR =
            System.getProperty("user.home") + File.separator + "BoatTicketSystem";

    private static final String DB_PATH =
            DB_DIR + File.separator + "tickets.db";

    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

    private static final DateTimeFormatter TICKET_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy  HH:mm:ss");

    // Password used to lock the exported sheet against editing.
    // The operator only needs to view/print/share the file, never edit cells,
    // so this password is intentionally not exposed anywhere in the UI.
    private static final String EXPORT_PROTECTION_PASSWORD = "SBB-Honnavar-ReadOnly-2026";

    private static DatabaseManager instance;

    private DatabaseManager() {
        try {
            new File(DB_DIR).mkdirs();
            copyBoatsCsvIfNeeded();
            initSchema();
            seedData();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to initialise database: " + e.getMessage(),
                    e
            );
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
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "username TEXT NOT NULL UNIQUE," +
                            "password TEXT NOT NULL," +
                            "role TEXT NOT NULL DEFAULT 'agent'" +
                            ")"
            );

            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS boats (" +
                            "id INTEGER PRIMARY KEY," +
                            "driver_name TEXT NOT NULL," +
                            "boat_hr_number TEXT NOT NULL," +
                            "boat_hon_number TEXT NOT NULL," +
                            "jetty TEXT NOT NULL," +
                            "capacity INTEGER NOT NULL DEFAULT 10," +
                            "contact TEXT NOT NULL" +
                            ")"
            );

            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS tickets (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "ticket_id TEXT NOT NULL UNIQUE," +
                            "booking_time TEXT NOT NULL," +
                            "customer_name TEXT NOT NULL," +
                            "contact_number TEXT NOT NULL," +
                            "num_people INTEGER NOT NULL," +
                            "ride_duration_hour INTEGER NOT NULL DEFAULT 1," +
                            "life_jacket INTEGER NOT NULL DEFAULT 0," +
                            "life_jacket_count INTEGER NOT NULL DEFAULT 0," +
                            "parking INTEGER NOT NULL DEFAULT 0," +
                            "vehicle_type TEXT," +
                            "vehicle_number TEXT," +
                            "boat_id INTEGER REFERENCES boats(id)," +
                            "boat_ride_fee INTEGER NOT NULL," +
                            "life_jacket_fee INTEGER NOT NULL DEFAULT 0," +
                            "parking_fee INTEGER NOT NULL DEFAULT 0," +
                            "total_fee INTEGER NOT NULL," +
                            "booked_by TEXT NOT NULL" +
                            ")"
            );
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

    private void insertUser(
            Connection connection,
            String username,
            String plainPassword,
            String role
    ) throws SQLException {
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users(username,password,role) VALUES(?,?,?)"
        )) {
            statement.setString(1, username);
            statement.setString(2, hash);
            statement.setString(3, role);
            statement.executeUpdate();
        }
    }

    private void seedBoats(Connection connection) throws SQLException {
        Path csvPath = Paths.get(DB_DIR, "boats.csv");

        try (BufferedReader reader = Files.exists(csvPath)
                ? Files.newBufferedReader(csvPath)
                : new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("boats.csv")
        ));
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO boats(id,driver_name,boat_hr_number,boat_hon_number," +
                             "jetty,capacity,contact) VALUES(?,?,?,?,?,?,?)"
             )) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                String[] parts = line.split(",");

                if (parts.length == 7) {
                    statement.setInt(1, Integer.parseInt(parts[0].trim()));
                    statement.setString(2, parts[1].trim());
                    statement.setString(3, parts[2].trim());
                    statement.setString(4, parts[3].trim());
                    statement.setString(5, parts[4].trim());
                    statement.setInt(6, Integer.parseInt(parts[5].trim()));
                    statement.setString(7, parts[6].trim());
                    statement.addBatch();
                }
            }

            statement.executeBatch();
        } catch (IOException e) {
            throw new SQLException("Failed to read boats.csv", e);
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
            try (InputStream inputStream =
                         getClass().getClassLoader().getResourceAsStream("boats.csv")) {
                if (inputStream != null) {
                    Files.copy(inputStream, csvPath);
                }
            }
        }
    }

    public String authenticate(String username, String password) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password, role FROM users WHERE username=?"
             )) {

            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()
                        && BCrypt.checkpw(password, resultSet.getString("password"))) {
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

        return connection.createStatement().executeQuery(
                "SELECT id, driver_name, boat_hr_number, boat_hon_number, " +
                        "jetty, capacity, contact FROM boats ORDER BY id"
        );
    }

    public ResultSet getBoatById(int id) throws SQLException {
        Connection connection = getConnection();

        PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM boats WHERE id=?"
        );

        statement.setInt(1, id);

        return statement.executeQuery();
    }

    public void saveTicket(Ticket ticket, int boatId, String bookedBy) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO tickets(ticket_id,booking_time,customer_name,contact_number," +
                             "num_people,life_jacket,life_jacket_count,parking,vehicle_type," +
                             "vehicle_number,boat_id,boat_ride_fee,life_jacket_fee,parking_fee," +
                             "total_fee,booked_by,ride_duration_hour) " +
                             "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
             )) {

            statement.setString(1, ticket.getTicketId());
            statement.setString(2, ticket.getFormattedBookingTime());
            statement.setString(3, ticket.getCustomerName());
            statement.setString(4, ticket.getContactNumber());
            statement.setInt(5, ticket.getNumberOfPeople());
            statement.setInt(6, ticket.isLifeJacketRequired() ? 1 : 0);
            statement.setInt(7, ticket.getLifeJacketCount());
            statement.setInt(8, ticket.isParkingRequired() ? 1 : 0);
            statement.setString(
                    9,
                    ticket.getVehicleType() != null
                            ? ticket.getVehicleType().name()
                            : null
            );
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
     * Exports tickets booked between fromDate and toDate (inclusive) to a
     * read-only .xlsx file. The sheet is protected (no password given to
     * the operator) so the file can only be viewed, printed, and shared —
     * not edited.
     *
     /**
     * Exports tickets booked between fromDate and toDate (inclusive) to a
     * read-only .xlsx file. The sheet is protected (no password given to
     * the operator) so the file can only be viewed, printed, and shared —
     * not edited.
     *
     * @return the exported File
     */
    public File exportToExcel(String destinationDir, LocalDate fromDate, LocalDate toDate)
            throws IOException, SQLException {

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        String rangeLabel =
                fromDate.format(DateTimeFormatter.ofPattern("ddMMyyyy")) +
                        "_to_" +
                        toDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"));

        Path filePath = Path.of(
                destinationDir,
                "tickets_export_" + rangeLabel + "_" + timestamp + ".xlsx"
        );

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

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT t.ticket_id, t.booking_time, t.customer_name, " +
                                 "t.contact_number, t.num_people, t.ride_duration_hour, " +
                                 "t.total_fee, b.driver_name, t.parking_fee " +
                                 "FROM tickets t LEFT JOIN boats b ON t.boat_id = b.id " +
                                 "ORDER BY t.booking_time DESC"
                 );
                 ResultSet resultSet = statement.executeQuery()) {

                // booking_time is stored as free-text "dd-MM-yyyy  HH:mm:ss",
                // which does not sort/filter correctly as a SQL string
                // comparison, so the date range is applied here in Java
                // after parsing each row with the same formatter used to
                // write it.
                while (resultSet.next()) {
                    String bookingTimeText = resultSet.getString("booking_time");
                    LocalDate bookingDate = parseBookingDate(bookingTimeText);

                    if (bookingDate == null
                            || bookingDate.isBefore(fromDate)
                            || bookingDate.isAfter(toDate)) {
                        continue;
                    }

                    Row row = sheet.createRow(rowNumber++);
                    row.createCell(0).setCellValue(resultSet.getString("ticket_id"));
                    row.createCell(1).setCellValue(bookingTimeText);
                    row.createCell(2).setCellValue(resultSet.getString("customer_name"));
                    row.createCell(3).setCellValue(resultSet.getString("contact_number"));
                    row.createCell(4).setCellValue(resultSet.getInt("num_people"));
                    row.createCell(5).setCellValue(resultSet.getInt("ride_duration_hour"));
                    row.createCell(6).setCellValue(resultSet.getInt("total_fee"));
                    row.createCell(7).setCellValue(resultSet.getString("driver_name"));
                    row.createCell(8).setCellValue(resultSet.getInt("parking_fee"));

                    totalParkingFee += resultSet.getInt("parking_fee");
                }
            }

            Row totalRow = sheet.createRow(rowNumber + 1);
            totalRow.createCell(0).setCellValue("Total Money Collected at Counter:");
            totalRow.createCell(1).setCellValue(totalParkingFee);

            Row rangeRow = sheet.createRow(rowNumber + 2);
            rangeRow.createCell(0).setCellValue("Date Range:");
            rangeRow.createCell(1).setCellValue(
                    fromDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) +
                            "  to  " +
                            toDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            );

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
        if (bookingTimeText == null || bookingTimeText.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(bookingTimeText.trim(), TICKET_TIME_FORMAT).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    public String getDbPath() {
        return DB_PATH;
    }

    public List<Ticket> getTodaysTickets() throws SQLException {
        List<Ticket> tickets = new ArrayList<>();

        String today = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        String query =
                "SELECT " +
                        "t.ticket_id, t.booking_time, t.customer_name, t.contact_number, " +
                        "t.num_people, t.life_jacket, t.life_jacket_count, t.parking, " +
                        "t.vehicle_type, t.vehicle_number, t.boat_ride_fee, " +
                        "t.life_jacket_fee, t.parking_fee, t.total_fee, t.booked_by, " +
                        "t.ride_duration_hour, " +
                        "b.id AS boat_id, b.driver_name, b.boat_hr_number, " +
                        "b.boat_hon_number, b.jetty, b.capacity, b.contact AS boat_contact " +
                        "FROM tickets t " +
                        "LEFT JOIN boats b ON t.boat_id = b.id " +
                        "WHERE t.booking_time LIKE ? " +
                        "ORDER BY t.booking_time DESC";

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
        LocalDateTime bookingTime = LocalDateTime.parse(
                resultSet.getString("booking_time"),
                TICKET_TIME_FORMAT
        );

        Ticket ticket = new Ticket(
                resultSet.getString("ticket_id"),
                bookingTime
        );

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
                resultSet.getInt("total_fee")
        );

        String vehicleType = resultSet.getString("vehicle_type");

        if (vehicleType != null && !vehicleType.isBlank()) {
            ticket.setVehicleType(Ticket.VehicleType.valueOf(vehicleType));
        }

        int boatId = resultSet.getInt("boat_id");

        if (!resultSet.wasNull()) {
            ticket.setBoatOwner(new BoatOwner(
                    boatId,
                    resultSet.getString("driver_name"),
                    resultSet.getString("boat_hr_number"),
                    resultSet.getString("boat_hon_number"),
                    resultSet.getString("jetty"),
                    resultSet.getInt("capacity"),
                    resultSet.getString("boat_contact")
            ));
        }

        return ticket;
    }
}