# 🚤 Boat Ride Ticket Booking System

A JavaFX 17 desktop application for booking boat ride tickets, with PDF generation.

---

## ✨ Features

| Feature | Details |
|---|---|
| Customer Details | Name + Contact Number |
| Passenger Count | Spinner (1–50 people) |
| Boat Ride Fee | ₹400 per person (1-hour ride, preset) |
| Life Jackets | Optional checkbox, ₹10 per jacket |
| Parking | Optional checkbox with 5 vehicle types |
| Vehicle Types | Two-Wheeler ₹10 · Car ₹20 · Auto ₹15 · Bus ₹40 · Truck ₹50 |
| Live Fee Calc | Total updates instantly as you fill the form |
| PDF Ticket | Professional A5 ticket with barcode, saved to your chosen folder |

---

## 🛠️ Prerequisites

- **Java 17** (JDK 17+)
- **Maven 3.8+**
- **IntelliJ IDEA** (Community or Ultimate)

---

## 🚀 Quick Start in IntelliJ

### 1. Open the Project
```
File → Open → select the BoatTicketSystem folder
```
IntelliJ will auto-detect the `pom.xml` and import dependencies.

### 2. Wait for Maven to download dependencies
The first run downloads JavaFX 17 and iText PDF (~5 MB total).

### 3. Run the app
- **Option A (Maven):** Right-click `pom.xml` → Run Maven → `javafx:run`
- **Option B (Main class):** Open `MainApp.java` → click the green ▶ Run button
- **Option C (Run Config):** The `.idea/runConfigurations/BoatTicketSystem.xml` is pre-configured

### If you see module errors, add VM options:
```
--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED
```
Go to: **Run → Edit Configurations → VM Options**

---

## 📁 Project Structure

```
BoatTicketSystem/
├── pom.xml                          ← Maven dependencies
├── build-exe.bat                    ← Build script for creating executable
├── src/
│   ├── assembly/
│   │   └── distribution.xml         ← Assembly descriptor for packaging
│   └── main/
│       ├── java/
│       │   ├── module-info.java     ← Java module descriptor
│       │   └── com/boatticket/
│       │       ├── MainApp.java     ← Entry point
│       │       ├── controller/
│       │       │   └── BookingController.java  ← UI logic
│       │       ├── model/
│       │       │   └── Ticket.java  ← Data model + fee constants
│       │       └── util/
│       │           └── PdfGenerator.java  ← iText PDF generation
│       └── resources/com/boatticket/
│           ├── booking.fxml         ← UI layout
│           └── styles.css           ← Styling
└── .idea/runConfigurations/         ← Pre-built IntelliJ run config
```

---

## 💰 Fee Structure (all preset — edit in `Ticket.java`)

```java
BOAT_RIDE_FEE_PER_PERSON  = 400   // 1-hour ride
LIFE_JACKET_FEE           = 10    // per jacket
PARKING_FEE_TWO_WHEELER   = 10
PARKING_FEE_CAR           = 20
PARKING_FEE_AUTO          = 15
PARKING_FEE_BUS           = 40
PARKING_FEE_TRUCK         = 50
```

To change any fee, open `src/main/java/com/boatticket/model/Ticket.java` and edit the constants at the top.

---

## 🖨️ Generating a Ticket PDF

1. Fill in the booking form
2. Click **"📄 Generate & Save PDF Ticket"**
3. Choose a folder in the dialog
4. Your ticket is saved as `Ticket_BT-XXXXXXXX.pdf`

The PDF includes:
- Unique Ticket ID + booking timestamp
- Customer & ride details
- Itemised fee breakdown
- Total amount
- Scannable barcode

---

## 🔧 Troubleshooting

**"JavaFX runtime components are missing"**
→ Make sure you're running via Maven (`mvn javafx:run`) or add `--module-path` to VM options.

**Dependencies not downloading**
→ Check internet connection, then: Right-click `pom.xml` → Maven → Reload Project.

**Build fails on module-info.java**
→ Ensure your IntelliJ is set to Java 17: File → Project Structure → SDK = 17.

---

## 📦 Building a runnable JAR

```bash
mvn clean package
java -jar target/BoatTicketSystem-1.0-SNAPSHOT.jar
```

---

## 🚀 Creating a Self-Contained Executable (Windows .exe)

This project is configured to create a self-contained executable that bundles all dependencies into a single JAR file and wraps it in a Windows executable.

### Build the distributable package:

**Option 1: Using the build script (recommended):**
```bash
# Double-click build-exe.bat or run:
build-exe.bat
```

**Option 2: Using Maven directly:**
```bash
mvn clean package
```

This will create:
- `target/BoatTicketSystem-2.0-SNAPSHOT.jar` - Fat JAR with all dependencies
- `target/BoatTicketSystem.exe` - Windows executable
- `target/BoatTicketSystem-2.0-SNAPSHOT-distribution.zip` - Complete distribution package

### What's included in the distribution:

- **Fat JAR** (created with Maven Shade) - Contains all dependencies (~25-35MB)
- **Windows executable** (`.exe`) launcher created with Launch4j
- **Batch file** launcher (`.bat`) as alternative
- **README** and documentation

### Running the application:

1. Extract the `BoatTicketSystem-2.0-SNAPSHOT-distribution.zip` file
2. Double-click `BoatTicketSystem.exe` or run `BoatTicketSystem.bat`
3. The application will start (requires Java 17+ on target machine)

### System Requirements for the executable:

- **Windows 7 SP1 or later** (64-bit recommended)
- **Java 17+** must be installed on the target machine
- **~30-40MB** free disk space for the extracted distribution

### How it works:

1. **Maven Shade Plugin** creates a fat JAR containing all dependencies
2. **Launch4j** wraps the JAR into a Windows executable with proper JRE detection
3. **Maven Assembly** packages everything into a distributable zip file

---

## 🔧 Advanced Build Options

### Build only the fat JAR (without exe):

```bash
mvn clean package -DskipTests
```

### Build only the executable (requires JAR to be built first):

```bash
mvn launch4j:launch4j
```

### Build only the distribution zip:

```bash
mvn assembly:single
```

### Run the application during development:

```bash
mvn javafx:run
```
