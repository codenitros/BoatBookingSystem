package com.boatticket.db;

import org.mindrot.jbcrypt.BCrypt;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
                "    id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    driver_name TEXT NOT NULL," +
                "    boat_name   TEXT NOT NULL," +
                "    boat_number TEXT NOT NULL," +
                "    capacity    INTEGER NOT NULL DEFAULT 10," +
                "    contact     TEXT" +
                ")");

            s.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tickets (" +
                "    id                INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    ticket_id         TEXT NOT NULL UNIQUE," +
                "    booking_time      TEXT NOT NULL," +
                "    customer_name     TEXT NOT NULL," +
                "    contact_number    TEXT NOT NULL," +
                "    num_people        INTEGER NOT NULL," +
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
            {"Ravi Kumar","Nandi","BT-001","12","9900011001"},
            {"Suresh Babu","Kaveri","BT-002","10","9900011002"},
            {"Manoj Reddy","Tungabhadra","BT-003","14","9900011003"},
            {"Kiran Sharma","Sharavathi","BT-004","10","9900011004"},
            {"Ajay Nair","Hemavathi","BT-005","12","9900011005"},
            {"Vijay Patil","Kabini","BT-006","10","9900011006"},
            {"Santosh Rao","Malaprabha","BT-007","14","9900011007"},
            {"Deepak Hegde","Bhadra","BT-008","10","9900011008"},
            {"Ramesh Gowda","Ghataprabha","BT-009","12","9900011009"},
            {"Sunil Joshi","Vedavathi","BT-010","10","9900011010"},
            {"Anil Verma","Palar","BT-011","14","9900011011"},
            {"Harish Shetty","Arkavathi","BT-012","10","9900011012"},
            {"Pradeep Kumar","Pennar","BT-013","12","9900011013"},
            {"Naresh Pillai","Bhima","BT-014","10","9900011014"},
            {"Gopal Naidu","Krishna","BT-015","14","9900011015"},
            {"Srinivas Murthy","Godavari","BT-016","10","9900011016"},
            {"Mohan Das","Cauvery","BT-017","12","9900011017"},
            {"Raju Yadav","Manjira","BT-018","10","9900011018"},
            {"Ashok Tiwari","Sabarmati","BT-019","14","9900011019"},
            {"Bhaskar Rao","Tapti","BT-020","10","9900011020"},
            {"Chandra Sekhar","Narmada","BT-021","12","9900011021"},
            {"Dinesh Babu","Betwa","BT-022","10","9900011022"},
            {"Ganesh Patel","Chambal","BT-023","14","9900011023"},
            {"Hanumantha Rao","Sindh","BT-024","10","9900011024"},
            {"Indra Kumar","Mahi","BT-025","12","9900011025"},
            {"Jagdish Singh","Luni","BT-026","10","9900011026"},
            {"Kalyan Raju","Banas","BT-027","14","9900011027"},
            {"Laxman Swamy","Rupen","BT-028","10","9900011028"},
            {"Madhu Sudan","Saraswati","BT-029","12","9900011029"},
            {"Nagesh Kumar","Ghaggar","BT-030","10","9900011030"},
            {"Omkar Nath","Yamuna","BT-031","14","9900011031"},
            {"Pavan Kalyan","Ganga","BT-032","10","9900011032"},
            {"Quamar Ali","Brahmaputra","BT-033","12","9900011033"},
            {"Ramakrishna","Indus","BT-034","10","9900011034"},
            {"Satish Chandra","Mahanadi","BT-035","14","9900011035"},
            {"Trivikram Rao","Brahmani","BT-036","10","9900011036"},
            {"Uday Shankar","Subarnarekha","BT-037","12","9900011037"},
            {"Venkatesh Babu","Damodar","BT-038","10","9900011038"},
            {"Wasim Khan","Teesta","BT-039","14","9900011039"},
            {"Xavier Pinto","Torsa","BT-040","10","9900011040"},
            {"Yogesh Kulkarni","Sankosh","BT-041","12","9900011041"},
            {"Zafarullah","Manas","BT-042","10","9900011042"},
            {"Abhijit Roy","Jaldhaka","BT-043","14","9900011043"},
            {"Bikash Mondal","Raidak","BT-044","10","9900011044"},
            {"Chiranjeevi","Kosi","BT-045","12","9900011045"},
            {"Dhananjay Patil","Bagmati","BT-046","10","9900011046"},
            {"Eswar Rao","Gandak","BT-047","14","9900011047"},
            {"Farhan Siddiqui","Ghaghra","BT-048","10","9900011048"},
            {"Girish Karnad","Rapti","BT-049","12","9900011049"},
            {"Hemanth Kumar","Sharda","BT-050","10","9900011050"},
            {"Irfan Pathan","Ramganga","BT-051","14","9900011051"},
            {"Jayaram Nair","Tons","BT-052","10","9900011052"},
            {"Karthik Subbu","Alaknanda","BT-053","12","9900011053"},
            {"Lokesh Yadav","Bhagirathi","BT-054","10","9900011054"},
            {"Mithun Chakra","Mandakini","BT-055","14","9900011055"},
            {"Niranjan Das","Pindar","BT-056","10","9900011056"},
            {"Omveer Singh","Dhauliganga","BT-057","12","9900011057"},
            {"Praveen Kumar","Saryu","BT-058","10","9900011058"},
            {"Qasim Ansari","Tamsa","BT-059","14","9900011059"},
            {"Rajkumar Rao","Gomti","BT-060","10","9900011060"},
            {"Sanjay Dutt","Betwa-2","BT-061","12","9900011061"},
            {"Tapan Ghosh","Ken","BT-062","10","9900011062"},
            {"Umesh Yadav","Sindhu","BT-063","14","9900011063"},
            {"Vinod Khanna","Paisuni","BT-064","10","9900011064"},
            {"Waqar Ahmed","Tons-MP","BT-065","12","9900011065"},
            {"Yashpal Sharma","Wainganga","BT-066","10","9900011066"},
            {"Zubair Khan","Wardha","BT-067","14","9900011067"},
            {"Aakash Mehta","Purna","BT-068","12","9900011068"},
            {"Baldev Singh","Penganga","BT-069","10","9900011069"},
            {"Chetan Sharma","Pranhita","BT-070","14","9900011070"},
            {"Devendra Fadke","Indravati","BT-071","10","9900011071"},
            {"Eknath Shinde","Sabari","BT-072","12","9900011072"},
            {"Firoz Khan","Sileru","BT-073","10","9900011073"},
            {"Govind Swami","Periyar","BT-074","14","9900011074"},
            {"Hemraj Meena","Bharathapuzha","BT-075","10","9900011075"},
            {"Ishaan Khatter","Pamba","BT-076","12","9900011076"},
            {"Jitender Singh","Chaliyar","BT-077","10","9900011077"},
            {"Kapil Dev","Kallada","BT-078","14","9900011078"},
            {"Lalu Prasad","Manimala","BT-079","10","9900011079"},
            {"Mahesh Babu","Achankovil","BT-080","12","9900011080"},
            {"Narayan Rao","Ithikkara","BT-081","10","9900011081"},
            {"Onkar Prasad","Karamana","BT-082","14","9900011082"},
            {"Prithviraj","Neyyar","BT-083","10","9900011083"},
            {"Qadir Hussain","Vamanapuram","BT-084","12","9900011084"},
            {"Ravindra Jadeja","Killiyar","BT-085","10","9900011085"},
            {"Suresh Raina","Meenachil","BT-086","14","9900011086"},
            {"Tilak Varma","Moovattupuzha","BT-087","10","9900011087"},
            {"Umran Malik","Thodupuzha","BT-088","12","9900011088"},
            {"Virat Kohli","Idamalayar","BT-089","10","9900011089"},
            {"Wasim Jaffer","Chalakudy","BT-090","14","9900011090"},
            {"Yuvraj Singh","Tirur","BT-091","10","9900011091"},
            {"Zaheer Khan","Kuppam","BT-092","12","9900011092"},
            {"Arun Kumar","Ponniyar","BT-093","10","9900011093"},
            {"Brijesh Patel","Vellar","BT-094","14","9900011094"},
            {"Chandra Kumar","Vaigai","BT-095","10","9900011095"},
            {"Dileep Kumar","Tamirabarani","BT-096","12","9900011096"},
            {"Elangovan","Kallar","BT-097","10","9900011097"},
            {"Faisal Ahmed","Siruvani","BT-098","14","9900011098"},
            {"Ganpat Rao","Bhavani","BT-099","10","9900011099"},
            {"Hari Shankar","Noyyal","BT-100","12","9900011100"},
            {"Imtiyaz Ali","Amaravathi","BT-101","10","9900011101"},
            {"Janardhan Rao","Kollidam","BT-102","14","9900011102"},
            {"Karunanidhi","Veeranam","BT-103","10","9900011103"},
            {"Lakshman Rao","Palar-TN","BT-104","12","9900011104"},
            {"Manohar Lal","Cheyyar","BT-105","10","9900011105"},
            {"Natarajan","Kosasthalaiyar","BT-106","14","9900011106"},
            {"Omprakash","Cooum","BT-107","10","9900011107"},
            {"Parthasarathy","Adyar","BT-108","12","9900011108"},
            {"Raghu Ram","Agniyar","BT-109","10","9900011109"},
            {"Selvam","Manimuthar","BT-110","14","9900011110"},
            {"Thiyagarajan","Hanumannadha","BT-111","10","9900011111"},
            {"Udayakumar","Pambar","BT-112","12","9900011112"},
            {"Venkatesan","Chittar","BT-113","10","9900011113"},
            {"Wangchuk","Moyar","BT-114","14","9900011114"},
            {"Xavier D'Souza","Bhavani-2","BT-115","10","9900011115"},
            {"Yaswanth Rao","Kabani","BT-116","12","9900011116"},
            {"Zafar Iqbal","Iruvanjipuzha","BT-117","10","9900011117"},
            {"Arjun Kapoor","Pampa","BT-118","14","9900011118"},
            {"Babu Antony","Achankovil-2","BT-119","10","9900011119"},
            {"Caesar D'Mello","Manimala-2","BT-120","12","9900011120"},
            {"Emmanuel Peter","Kallada-2","BT-121","10","9900011121"},
            {"Francis Xavier","Periyar-2","BT-122","14","9900011122"},
            {"George Thomas","Chalakudy-2","BT-123","10","9900011123"},
            {"Henry Joseph","Bharathapuzha-2","BT-124","12","9900011124"},
            {"Isaac Nathan","Karuvannur","BT-125","10","9900011125"},
            {"Jacob Mathew","Muvattupuzha-2","BT-126","14","9900011126"},
            {"Kuriakose","Meenachil-2","BT-127","10","9900011127"},
            {"Liju Paul","Thodupuzha-2","BT-128","12","9900011128"},
            {"Mathew Philip","Idamalayar-2","BT-129","10","9900011129"},
            {"Ninan Thomas","Chaliyar-2","BT-130","14","9900011130"},
            {"Ouseph Alex","Ithikkara-2","BT-131","10","9900011131"},
            {"Paulose Varkey","Kallada-3","BT-132","12","9900011132"},
            {"Rajan Pillai","Vamanapuram-2","BT-133","10","9900011133"},
            {"Sebastian Thomas","Neyyar-2","BT-134","14","9900011134"},
            {"Thomas Kurian","Killiyar-2","BT-135","10","9900011135"},
            {"Unni Krishnan","Karamana-2","BT-136","12","9900011136"},
            {"Varghese Jose","Achankovil-3","BT-137","10","9900011137"},
            {"Wilson D'Souza","Periyar-3","BT-138","14","9900011138"},
            {"Xavier Rodrigues","Bharathapuzha-3","BT-139","10","9900011139"},
            {"Yohannan Kurien","Chalakudy-3","BT-140","12","9900011140"},
            {"Zacharias Joseph","Manimala-3","BT-141","10","9900011141"},
            {"Abhilash Nair","Pambar-2","BT-142","14","9900011142"},
            {"Benny Abraham","Muvattupuzha-3","BT-143","10","9900011143"},
            {"Ciriac Jose","Meenachil-3","BT-144","12","9900011144"},
            {"Devassy Philip","Idamalayar-3","BT-145","10","9900011145"},
            {"Eldho Kurian","Chaliyar-3","BT-146","14","9900011146"},
            {"Felix Thomas","Ithikkara-3","BT-147","10","9900011147"},
            {"Geevarghese","Karamana-3","BT-148","12","9900011148"},
            {"Hormis Korath","Neyyar-3","BT-149","10","9900011149"},
            {"Idicula Mathew","Killiyar-3","BT-150","14","9900011150"},
        };

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO boats(driver_name,boat_name,boat_number,capacity,contact) VALUES(?,?,?,?,?)")) {
            for (String[] b : boats) {
                ps.setString(1, b[0]); ps.setString(2, b[1]);
                ps.setString(3, b[2]); ps.setInt(4, Integer.parseInt(b[3]));
                ps.setString(5, b[4]);
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
                "SELECT id, driver_name, boat_name, boat_number, capacity, contact FROM boats ORDER BY driver_name");
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
                "boat_id,boat_ride_fee,life_jacket_fee,parking_fee,total_fee,booked_by) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
            ps.executeUpdate();
        }
    }

    // ── Backup ────────────────────────────────────────────────────────────────
    public void backup(String destinationDir) throws IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path src  = Path.of(DB_PATH);
        Path dest = Path.of(destinationDir, "tickets_backup_" + ts + ".db");
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    public String getDbPath() { return DB_PATH; }
}
