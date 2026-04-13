package com.boatticket.controller;

import com.boatticket.MainApp;
import com.boatticket.db.DatabaseManager;
import com.boatticket.model.BoatOwner;
import com.boatticket.model.Ticket;
import com.boatticket.util.PdfGenerator;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.sql.ResultSet;
import java.util.ResourceBundle;

public class BookingController implements Initializable {

    // Customer
    @FXML private TextField customerNameField;
    @FXML private TextField contactNumberField;

    // Ride
    @FXML private Spinner<Integer> peopleSpinner;
    @FXML private Label            boatFeeLabel;

    // Life jacket
    @FXML private CheckBox         lifeJacketCheckBox;
    @FXML private HBox             lifeJacketBox;
    @FXML private Spinner<Integer> jacketSpinner;
    @FXML private Label            jacketFeeLabel;

    // Parking
    @FXML private CheckBox    parkingCheckBox;
    @FXML private VBox        parkingBox;
    @FXML private ToggleGroup vehicleGroup;
    @FXML private RadioButton rbTwoWheeler, rbCar, rbBus, rbTruck;
    @FXML private TextField   vehicleNumberField;
    @FXML private Label       parkingFeeLabel;

    // Boat assignment
    @FXML private TextField             boatSearchField;
    @FXML private ComboBox<BoatOwner>   boatComboBox;
    @FXML private Label                 boatDetailsLabel;

    // Summary
    @FXML private Label totalFeeLabel;
    @FXML private Label driverAmountLabel;
    @FXML private Label counterAmountLabel;
    @FXML private Label statusLabel;
    @FXML private Label loggedInLabel;

    // Buttons
    @FXML private Button generateBtn;
    @FXML private Button resetBtn;
    @FXML private Button backupBtn;
    @FXML private Button logoutBtn;

    private ObservableList<BoatOwner> allBoats = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loggedInLabel.setText("Logged in as:  " + MainApp.loggedInUser + "  [" + MainApp.loggedInRole + "]");

        // Spinners
        peopleSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 1));
        peopleSpinner.setEditable(true);
        jacketSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 1));
        jacketSpinner.setEditable(true);

        // Hide conditional panels
        lifeJacketBox.setVisible(false); lifeJacketBox.setManaged(false);
        parkingBox.setVisible(false);    parkingBox.setManaged(false);

        // Radio button userData
        rbTwoWheeler.setUserData(Ticket.VehicleType.TWO_WHEELER);
        rbCar.setUserData(Ticket.VehicleType.CAR);
        rbBus.setUserData(Ticket.VehicleType.BUS);
        rbTruck.setUserData(Ticket.VehicleType.TRUCK);

        // Load boats into ComboBox
        loadBoats();

        // Live search filter on boat combo
        boatSearchField.textProperty().addListener((obs, o, term) -> {
            String lower = term.toLowerCase();
            ObservableList<BoatOwner> filtered = FXCollections.observableArrayList();
            for (BoatOwner b : allBoats) {
                if (b.getDriverName().toLowerCase().contains(lower)
                        || b.getBoatName().toLowerCase().contains(lower)
                        || b.getBoatNumber().toLowerCase().contains(lower)) {
                    filtered.add(b);
                }
            }
            boatComboBox.setItems(filtered);
            if (!filtered.isEmpty()) boatComboBox.show();
        });

        boatComboBox.setOnAction(e -> {
            BoatOwner sel = boatComboBox.getValue();
            if (sel != null) {
                boatDetailsLabel.setText(
                        "Driver: " + sel.getDriverName() +
                        "  |  Boat: " + sel.getBoatName() +
                        " (" + sel.getBoatNumber() + ")" +
                        "  |  Capacity: " + sel.getCapacity() +
                        "  |  Contact: " + sel.getContact());
            } else {
                boatDetailsLabel.setText("");
            }
        });

        // Fee listeners
        peopleSpinner.valueProperty().addListener((obs, o, n) -> refreshFees());
        lifeJacketCheckBox.selectedProperty().addListener((obs, o, selected) -> {
            lifeJacketBox.setVisible(selected); lifeJacketBox.setManaged(selected);
            if (selected) jacketSpinner.getValueFactory().setValue(peopleSpinner.getValue());
            refreshFees();
        });
        jacketSpinner.valueProperty().addListener((obs, o, n) -> refreshFees());
        parkingCheckBox.selectedProperty().addListener((obs, o, selected) -> {
            parkingBox.setVisible(selected); parkingBox.setManaged(selected);
            refreshFees();
        });
        vehicleGroup.selectedToggleProperty().addListener((obs, o, n) -> refreshFees());

        refreshFees();
    }

    private void loadBoats() {
        try (ResultSet rs = DatabaseManager.getInstance().getAllBoats()) {
            while (rs.next()) {
                allBoats.add(new BoatOwner(
                        rs.getInt("id"),
                        rs.getString("driver_name"),
                        rs.getString("boat_name"),
                        rs.getString("boat_number"),
                        rs.getInt("capacity"),
                        rs.getString("contact")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        boatComboBox.setItems(allBoats);
    }

    private void refreshFees() {
        int people    = safeInt(peopleSpinner, 1);
        int jackets   = safeInt(jacketSpinner, 1);
        int boatFee   = Ticket.BOAT_RIDE_FEE_PER_HOUR ;
        int jacketFee = lifeJacketCheckBox.isSelected() ? Ticket.LIFE_JACKET_FEE * jackets : 0;
        int parkFee   = getParkingFeeFromUI();
        int toDriver  = boatFee + jacketFee;
        int total     = toDriver + parkFee;

        boatFeeLabel.setText("Rs " + boatFee);
        jacketFeeLabel.setText("Rs " + jacketFee);
        parkingFeeLabel.setText("Rs " + parkFee);
        driverAmountLabel.setText("Rs " + toDriver);
        counterAmountLabel.setText("Rs " + parkFee);
        totalFeeLabel.setText("Rs " + total);
    }

    private int getParkingFeeFromUI() {
        if (!parkingCheckBox.isSelected()) return 0;
        Toggle t = vehicleGroup.getSelectedToggle();
        if (t == null) return 0;
        Ticket.VehicleType vt = (Ticket.VehicleType) t.getUserData();
        if (vt == null) return 0;
        return switch (vt) {
            case TWO_WHEELER -> Ticket.PARKING_FEE_TWO_WHEELER;
            case CAR         -> Ticket.PARKING_FEE_CAR;
            case AUTO        -> Ticket.PARKING_FEE_AUTO;
            case BUS         -> Ticket.PARKING_FEE_BUS;
            case TRUCK       -> Ticket.PARKING_FEE_TRUCK;
        };
    }

    @FXML
    private void handleGenerateTicket() {
        if (!validateForm()) return;

        Ticket ticket = new Ticket();
        ticket.setCustomerName(customerNameField.getText().trim());
        ticket.setContactNumber(contactNumberField.getText().trim());
        ticket.setNumberOfPeople(safeInt(peopleSpinner, 1));
        ticket.setLifeJacketRequired(lifeJacketCheckBox.isSelected());
        ticket.setLifeJacketCount(lifeJacketCheckBox.isSelected() ? safeInt(jacketSpinner, 1) : 0);
        ticket.setParkingRequired(parkingCheckBox.isSelected());
        if (parkingCheckBox.isSelected() && vehicleGroup.getSelectedToggle() != null) {
            ticket.setVehicleType((Ticket.VehicleType) vehicleGroup.getSelectedToggle().getUserData());
            ticket.setVehicleNumber(vehicleNumberField.getText().trim());
        }
        ticket.setBoatOwner(boatComboBox.getValue());
        ticket.setBookedBy(MainApp.loggedInUser);

        // Save to DB
        try {
            DatabaseManager.getInstance().saveTicket(ticket, boatComboBox.getValue().getId(), MainApp.loggedInUser);
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("❌  DB error: " + e.getMessage(), false);
            return;
        }

        // Save PDF
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose folder to save ticket PDF");
        dc.setInitialDirectory(new File(System.getProperty("user.home")));
        File dir = dc.showDialog(generateBtn.getScene().getWindow());
        if (dir == null) return;

        String path = dir.getAbsolutePath() + File.separator + "Ticket_" + ticket.getTicketId() + ".pdf";
        try {
            PdfGenerator.generate(ticket, path);
            showStatus("Ticket saved: " + path, true);
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("❌  PDF error: " + e.getMessage(), false);
        }
    }

    @FXML
    private void handleReset() {
        customerNameField.clear();
        contactNumberField.clear();
        peopleSpinner.getValueFactory().setValue(1);
        lifeJacketCheckBox.setSelected(false);
        jacketSpinner.getValueFactory().setValue(1);
        parkingCheckBox.setSelected(false);
        vehicleGroup.selectToggle(null);
        vehicleNumberField.clear();
        boatSearchField.clear();
        boatComboBox.setValue(null);
        boatComboBox.setItems(allBoats);
        boatDetailsLabel.setText("");
        statusLabel.setText("");
        refreshFees();
    }

    @FXML
    private void handleBackup() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose backup destination folder");
        dc.setInitialDirectory(new File(System.getProperty("user.home")));
        File dir = dc.showDialog(backupBtn.getScene().getWindow());
        if (dir == null) return;
        try {
            DatabaseManager.getInstance().backup(dir.getAbsolutePath());
            showStatus("Backup saved to: " + dir.getAbsolutePath(), true);
        } catch (Exception e) {
            showStatus("❌  Backup failed: " + e.getMessage(), false);
        }
    }

    @FXML
    private void handleLogout() {
        try {
            MainApp.loggedInUser = "";
            MainApp.loggedInRole = "";
            MainApp.showLogin((javafx.stage.Stage) logoutBtn.getScene().getWindow());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private boolean validateForm() {
        if (customerNameField.getText().isBlank()) {
            showStatus("Please enter customer name.", false);
            customerNameField.requestFocus(); return false;
        }
        if (contactNumberField.getText().isBlank()) {
            showStatus("Please enter contact number.", false);
            contactNumberField.requestFocus(); return false;
        }
        if (boatComboBox.getValue() == null) {
            showStatus("Please select a boat / driver.", false); return false;
        }
        if (parkingCheckBox.isSelected() && vehicleGroup.getSelectedToggle() == null) {
            showStatus("Please select a vehicle type.", false); return false;
        }
        return true;
    }

    private int safeInt(Spinner<Integer> s, int fallback) {
        try { Integer v = s.getValue(); return (v != null && v > 0) ? v : fallback; }
        catch (Exception e) { return fallback; }
    }

    private void showStatus(String msg, boolean success) {
        statusLabel.setText(msg);
        statusLabel.setStyle(success
                ? "-fx-text-fill: #228b22; -fx-font-weight: bold;"
                : "-fx-text-fill: #c0392b; -fx-font-weight: bold;");
        FadeTransition ft = new FadeTransition(Duration.millis(300), statusLabel);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }
}
