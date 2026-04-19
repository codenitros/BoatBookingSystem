package com.boatticket.db;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mindrot.jbcrypt.BCrypt;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database manager.
 * DB stored at: <user.home>/BoatTicketSystem/tickets.db
 * Each desktop is independent — no network needed.
 */
public class DatabaseManager {

    private static final String DB_DIR  = System.getProperty("user.home") + File.separator + "BoatTicketSystem";
    private static final String DB_PATH = DB_DIR + File.separator + "tickets.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

    private static DatabaseManager instance;

    private DatabaseManager() {
        try {
            new File(DB_DIR).mkdirs();
            initSchema();
            seedData();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise database: " + e.getMessage(), e);
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL);
    }

    // ── Schema ────────────────────────────────────────────────────────────────
    private void initSchema() throws SQLException {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "    id       INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    username TEXT NOT NULL UNIQUE," +
                "    password TEXT NOT NULL," +
                "    role     TEXT NOT NULL DEFAULT 'agent'" +
                ")");

            s.executeUpdate(
                "CREATE TABLE IF NOT EXISTS boats (" +
                "    id          INTEGER PRIMARY KEY," +
                "    driver_name TEXT NOT NULL," +
                "    boat_hr_number TEXT NOT NULL," +
                "    boat_hon_number TEXT NOT NULL," +
                "    jetty       TEXT NOT NULL," +
                "    capacity    INTEGER NOT NULL DEFAULT 10," +
                "    contact     TEXT NOT NULL" +
                ")");

            s.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tickets (" +
                "    id                INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    ticket_id         TEXT NOT NULL UNIQUE," +
                "    booking_time      TEXT NOT NULL," +
                "    customer_name     TEXT NOT NULL," +
                "    contact_number    TEXT NOT NULL," +
                "    num_people        INTEGER NOT NULL," +
                "    ride_duration_hour INTEGER NOT NULL DEFAULT 1," +
                "    life_jacket       INTEGER NOT NULL DEFAULT 0," +
                "    life_jacket_count INTEGER NOT NULL DEFAULT 0," +
                "    parking           INTEGER NOT NULL DEFAULT 0," +
                "    vehicle_type      TEXT," +
                "    vehicle_number    TEXT," +
                "    boat_id           INTEGER REFERENCES boats(id)," +
                "    boat_ride_fee     INTEGER NOT NULL," +
                "    life_jacket_fee   INTEGER NOT NULL DEFAULT 0," +
                "    parking_fee       INTEGER NOT NULL DEFAULT 0," +
                "    total_fee         INTEGER NOT NULL," +
                "    booked_by         TEXT NOT NULL" +
                ")");
        }
    }

    // ── Seed ──────────────────────────────────────────────────────────────────
    private void seedData() throws SQLException {
        try (Connection c = getConnection()) {
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.getInt(1) == 0) {
                    insertUser(c, "admin",  "admin@123",  "admin");
                    insertUser(c, "agent1", "agent1@123", "agent");
                    insertUser(c, "agent2", "agent2@123", "agent");
                    insertUser(c, "agent3", "agent3@123", "agent");
                }
            }
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM boats")) {
                if (rs.getInt(1) == 0) seedBoats(c);
            }
        }
    }

    private void insertUser(Connection c, String username, String plainPwd, String role) throws SQLException {
        String hash = BCrypt.hashpw(plainPwd, BCrypt.gensalt());
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users(username,password,role) VALUES(?,?,?)")) {
            ps.setString(1, username); ps.setString(2, hash); ps.setString(3, role);
            ps.executeUpdate();
        }
    }

    private void seedBoats(Connection c) throws SQLException {
        String[][] boats = {
            {"1","Ravi Kumar","HR-001","HON-568","jetty-a","8","9900011001"},
            {"2","Suresh Babu","HR-002","HON-009","jetty-a","8","9900011002"},
            {"3","Manoj Reddy","HR-003","HON-532","jetty-b","6","9900011003"},
            {"4","Kiran Sharma","HR-004","HON-765","jetty-b","8","9900011004"},
            {"5","Ajay Nair","HR-005","HON-453","jetty-c","6","9900011005"},
            {"6","Vijay Patil","HR-006","HON-123","jetty-c","6","9900011006"},
        };

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO boats(id,driver_name,boat_hr_number,boat_hon_number,jetty,capacity,contact) VALUES(?,?,?,?,?,?,?)")) {
        for (String[] b : boats) {
            ps.setInt(1, Integer.parseInt(b[0]));
            ps.setString(2, b[1]);
            ps.setString(3, b[2]);
            ps.setString(4, b[3]);
            ps.setString(5, b[4]);
            ps.setInt(6, Integer.parseInt(b[5]));
            ps.setString(7, b[6]);
            ps.addBatch();
        }
            ps.executeBatch();
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────
    /** Returns role string if credentials valid, null otherwise. */
    public String authenticate(String username, String password) {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT password, role FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && BCrypt.checkpw(password, rs.getString("password")))
                    return rs.getString("role");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    // ── Boat queries ──────────────────────────────────────────────────────────
    public ResultSet getAllBoats() throws SQLException {
        Connection c = getConnection();
        return c.createStatement().executeQuery(
                "SELECT id, driver_name, boat_hr_number, boat_hon_number, jetty, capacity, contact FROM boats ORDER BY id");
    }

    public ResultSet getBoatById(int id) throws SQLException {
        Connection c = getConnection();
        PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM boats WHERE id=?");
        ps.setInt(1, id);
        return ps.executeQuery();
    }

    // ── Ticket insert ─────────────────────────────────────────────────────────
    public void saveTicket(com.boatticket.model.Ticket t, int boatId, String bookedBy) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tickets(ticket_id,booking_time,customer_name,contact_number," +
                "num_people,life_jacket,life_jacket_count,parking,vehicle_type,vehicle_number," +
                "boat_id,boat_ride_fee,life_jacket_fee,parking_fee,total_fee,booked_by,ride_duration_hour) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, t.getTicketId());
            ps.setString(2, t.getFormattedBookingTime());
            ps.setString(3, t.getCustomerName());
            ps.setString(4, t.getContactNumber());
            ps.setInt(5, t.getNumberOfPeople());
            ps.setInt(6, t.isLifeJacketRequired() ? 1 : 0);
            ps.setInt(7, t.getLifeJacketCount());
            ps.setInt(8, t.isParkingRequired() ? 1 : 0);
            ps.setString(9, t.getVehicleType() != null ? t.getVehicleType().name() : null);
            ps.setString(10, t.getVehicleNumber());
            ps.setInt(11, boatId);
            ps.setInt(12, t.getBoatRideFee());
            ps.setInt(13, t.getLifeJacketFee());
            ps.setInt(14, t.getParkingFee());
            ps.setInt(15, t.getParkingFee()); // payable at counter = parking only
            ps.setString(16, bookedBy);
            ps.setInt(17, t.getRideDurationInHour());
            ps.executeUpdate();
        }
    }

    // ── Export to Excel ────────────────────────────────────────────────────────
    public void exportToExcel(String destinationDir) throws IOException, SQLException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "tickets_export_" + ts + ".xlsx";
        Path filePath = Path.of(destinationDir, fileName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Tickets");

            // Header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Ticket ID", "Booking Time", "Customer Name", "Contact Number", "Number of People", "Duration (Hours)", "Total Fee", "Driver Name", "Parking Fee"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Data rows
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT t.ticket_id, t.booking_time, t.customer_name, t.contact_number, t.num_people, t.ride_duration_hour, t.total_fee, b.driver_name, t.parking_fee " +
                         "FROM tickets t LEFT JOIN boats b ON t.boat_id = b.id ORDER BY t.booking_time DESC");
                 ResultSet rs = ps.executeQuery()) {

                int rowNum = 1;
                while (rs.next()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(rs.getString("ticket_id"));
                    row.createCell(1).setCellValue(rs.getString("booking_time"));
                    row.createCell(2).setCellValue(rs.getString("customer_name"));
                    row.createCell(3).setCellValue(rs.getString("contact_number"));
                    row.createCell(4).setCellValue(rs.getInt("num_people"));
                    row.createCell(5).setCellValue(rs.getInt("ride_duration_hour"));
                    row.createCell(6).setCellValue(rs.getInt("total_fee"));
                    row.createCell(7).setCellValue(rs.getString("driver_name"));
                    row.createCell(8).setCellValue(rs.getInt("parking_fee"));
                }

                // Total counter collection
                Row totalRow = sheet.createRow(rowNum + 1);
                totalRow.createCell(0).setCellValue("Total Money Collected at Counter:");
                try (PreparedStatement psTotal = c.prepareStatement("SELECT SUM(parking_fee) AS total FROM tickets");
                     ResultSet rsTotal = psTotal.executeQuery()) {
                    if (rsTotal.next()) {
                        totalRow.createCell(1).setCellValue(rsTotal.getInt("total"));
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }
    }

    public String getDbPath() { return DB_PATH; }

    // ── Ticket queries ─────────────────────────────────────────────────────────
    public List<String> getTodaysTickets() throws SQLException {
        List<String> tickets = new ArrayList<>();
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT b.id, t.booking_time, b.driver_name " +
                     "FROM tickets t LEFT JOIN boats b ON t.boat_id = b.id " +
                     "WHERE t.booking_time LIKE ? ORDER BY t.booking_time DESC")) {
            ps.setString(1, today + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String info = String.format("%s | %s | Driver: %s",
                            rs.getString("id"),
                            rs.getString("booking_time"),
                            rs.getString("driver_name"));
                    tickets.add(info);
                }
            }
        }
        return tickets;
    }
}
