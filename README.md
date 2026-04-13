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
├── src/main/
│   ├── java/
│   │   ├── module-info.java         ← Java module descriptor
│   │   └── com/boatticket/
│   │       ├── MainApp.java         ← Entry point
│   │       ├── controller/
│   │       │   └── BookingController.java  ← UI logic
│   │       ├── model/
│   │       │   └── Ticket.java      ← Data model + fee constants
│   │       └── util/
│   │           └── PdfGenerator.java  ← iText PDF generation
│   └── resources/com/boatticket/
│       ├── booking.fxml             ← UI layout
│       └── styles.css               ← Styling
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
