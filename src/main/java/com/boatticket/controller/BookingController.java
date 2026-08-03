package com.boatticket.controller;

import com.boatticket.MainApp;
import com.boatticket.db.DatabaseManager;
import com.boatticket.model.BoatOwner;
import com.boatticket.model.Ticket;
import com.boatticket.util.ThermalPrinterManager;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.sql.ResultSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;

public class BookingController implements Initializable {

    private static final int MAX_PASSENGERS_WITHOUT_BOAT = 50;

    // Customer
    @FXML private TextField customerNameField;
    @FXML private TextField contactNumberField;
    @FXML private Label contactNumberValidationLabel;

    // Ride
    @FXML private Spinner<Integer> peopleSpinner;
    @FXML private Label boatFeeLabel;
    @FXML private RadioButton rb1Hour, rb2Hour;
    @FXML private CheckBox parkingCheckBox;
    @FXML private VBox parkingBox;
    @FXML private ToggleGroup vehicleGroup;
    @FXML private RadioButton rbCar, rbBus, rbTempo;
    @FXML private TextField vehicleNumberField;
    @FXML private Label parkingFeeLabel;
    @FXML private Label passengerCapacityValidationLabel;

    // Boat assignment
    @FXML private TextField boatSearchField;
    @FXML private ComboBox<BoatOwner> boatComboBox;
    @FXML private Label boatDetailsLabel;

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
    @FXML private Button viewTicketsBtn;
    @FXML private Button logoutBtn;

    private ObservableList<BoatOwner> allBoats = FXCollections.observableArrayList();

    @FXML private ToggleGroup durationGroup;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loggedInLabel.setText("Logged in as:  " + MainApp.loggedInUser + "  [" + MainApp.loggedInRole + "]");

        configureContactNumberField();

        contactNumberField.textProperty().addListener((obs, oldValue, newValue) ->
                updateContactNumberValidationMessage());

        // Spinners
        peopleSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, MAX_PASSENGERS_WITHOUT_BOAT, 1));
        peopleSpinner.setEditable(true);
        configurePeopleSpinnerEditor();

        parkingBox.setVisible(false);
        parkingBox.setManaged(false);

        // Radio button userData
        rbCar.setUserData(Ticket.VehicleType.CAR);
        rbBus.setUserData(Ticket.VehicleType.BUS);
        rbTempo.setUserData(Ticket.VehicleType.TEMPOTRAVELLER);

        // Duration group

        rb1Hour.setUserData(1);
        rb2Hour.setUserData(2);
        rb1Hour.setSelected(true);

        // Load boats into ComboBox
        loadBoats();

        // Live search filter on boat combo
        boatSearchField.textProperty().addListener((obs, o, term) -> {
            String lower = term.toLowerCase();
            ObservableList<BoatOwner> filtered = FXCollections.observableArrayList();

            for (BoatOwner boat : allBoats) {
                if (boat.getDriverName().toLowerCase().contains(lower)
                        || boat.getBoatHRNumber().toLowerCase().contains(lower)
                        || boat.getBoatHONumber().toLowerCase().contains(lower)
                        || boat.getJetty().toLowerCase().contains(lower)
                        || String.valueOf(boat.getCapacity()).contains(lower)) {
                    filtered.add(boat);
                }
            }

            boatComboBox.setItems(filtered);

            if (!filtered.isEmpty()) {
                boatComboBox.show();
            }
        });

        boatComboBox.setOnAction(e -> {
            BoatOwner selectedBoat = boatComboBox.getValue();

            if (selectedBoat != null) {
                boatDetailsLabel.setText(
                        "Number: " + selectedBoat.getId() +
                                "  | Driver: " + selectedBoat.getDriverName() +
                                "  |  Boat HR Number: " + selectedBoat.getBoatHRNumber() +
                                "  |  Boat HON Number: " + selectedBoat.getBoatHONumber() +
                                "  |  Jetty: " + selectedBoat.getJetty() +
                                "  |  Capacity: " + selectedBoat.getCapacity() +
                                "  |  Contact: " + selectedBoat.getContact());

                updatePassengerCapacity();
            } else {
                boatDetailsLabel.setText("");
                resetPassengerCapacity();
            }

            passengerCapacityValidationLabel.setText("");
        });

        // Fee listeners
        peopleSpinner.valueProperty().addListener((obs, o, n) -> refreshFees());
        durationGroup.selectedToggleProperty().addListener((obs, o, n) -> refreshFees());

        parkingCheckBox.selectedProperty().addListener((obs, o, selected) -> {
            parkingBox.setVisible(selected);
            parkingBox.setManaged(selected);
            refreshFees();
        });

        vehicleGroup.selectedToggleProperty().addListener((obs, o, n) -> refreshFees());

        refreshFees();
    }

    private void configureContactNumberField() {
        UnaryOperator<TextFormatter.Change> contactNumberFilter = change -> {
            String newValue = change.getControlNewText();

            return newValue.matches("\\d{0,10}") ? change : null;
        };

        contactNumberField.setTextFormatter(new TextFormatter<>(contactNumberFilter));
    }

    private void configurePeopleSpinnerEditor() {
        UnaryOperator<TextFormatter.Change> passengerFilter = change -> {
            String newValue = change.getControlNewText();

            if (newValue.isBlank()) {
                passengerCapacityValidationLabel.setText("");
                return change;
            }

            try {
                int passengers = Integer.parseInt(newValue);

                if (passengers < 1 || passengers > MAX_PASSENGERS_WITHOUT_BOAT) {
                    return null;
                }

                BoatOwner selectedBoat = boatComboBox.getValue();

                if (selectedBoat != null && passengers > selectedBoat.getCapacity()) {
                    passengerCapacityValidationLabel.setText(
                            "Maximum passengers allowed: " + selectedBoat.getCapacity());
                    return null;
                }

                passengerCapacityValidationLabel.setText("");
                return change;
            } catch (NumberFormatException e) {
                return null;
            }
        };

        peopleSpinner.getEditor().setTextFormatter(new TextFormatter<>(passengerFilter));
    }

    private void updateContactNumberValidationMessage() {
        String phone = contactNumberField.getText().trim();

        if (!phone.isBlank() && phone.length() < 10) {
            contactNumberValidationLabel.setText("Enter 10-digit contact number.");
        } else {
            contactNumberValidationLabel.setText("");
        }
    }

    private int getSelectedBoatCapacity() {
        BoatOwner selectedBoat = boatComboBox.getValue();

        if (selectedBoat == null) {
            return MAX_PASSENGERS_WITHOUT_BOAT;
        }

        return Math.max(1, selectedBoat.getCapacity());
    }

    private void updatePassengerCapacity() {
        int maximumCapacity = getSelectedBoatCapacity();
        int currentPassengers = safeInt(peopleSpinner, 1);
        int allowedPassengers = Math.min(currentPassengers, maximumCapacity);

        peopleSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, maximumCapacity, allowedPassengers));

        refreshFees();
    }

    private void resetPassengerCapacity() {
        int currentPassengers = safeInt(peopleSpinner, 1);
        int allowedPassengers = Math.min(currentPassengers, MAX_PASSENGERS_WITHOUT_BOAT);

        peopleSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, MAX_PASSENGERS_WITHOUT_BOAT, allowedPassengers));

        refreshFees();
    }

    private void loadBoats() {
        try (ResultSet rs = DatabaseManager.getInstance().getAllBoats()) {
            while (rs.next()) {
                allBoats.add(new BoatOwner(
                        rs.getInt("id"),
                        rs.getString("driver_name"),
                        rs.getString("boat_hr_number"),
                        rs.getString("boat_hon_number"),
                        rs.getString("jetty"),
                        rs.getInt("capacity"),
                        rs.getString("contact")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        boatComboBox.setItems(allBoats);
    }

    private void refreshFees() {
        int people = safeInt(peopleSpinner, 1);
        int duration = getDurationFromUI();

        int boatFee = (duration == 2)
                ? Ticket.BOAT_RIDE_FEE_2HOURS
                : Ticket.BOAT_RIDE_FEE_PER_HOUR * duration;

        int jacketFee = Ticket.LIFE_JACKET_FEE * people;
        int parkFee = getParkingFeeFromUI();
        int toDriver = boatFee + jacketFee;
        int total = toDriver + parkFee;

        boatFeeLabel.setText("Rs " + boatFee);
        parkingFeeLabel.setText("Rs " + parkFee);
        driverAmountLabel.setText("Rs " + toDriver);
        counterAmountLabel.setText("Rs " + parkFee);
        totalFeeLabel.setText("Rs " + total);
    }

    private int getParkingFeeFromUI() {
        if (!parkingCheckBox.isSelected()) {
            return 0;
        }

        Toggle selectedToggle = vehicleGroup.getSelectedToggle();

        if (selectedToggle == null) {
            return 0;
        }

        Ticket.VehicleType vehicleType =
                (Ticket.VehicleType) selectedToggle.getUserData();

        if (vehicleType == null) {
            return 0;
        }

        return switch (vehicleType) {
            case CAR -> Ticket.PARKING_FEE_CAR;
            case BUS -> Ticket.PARKING_FEE_BUS;
            case TEMPOTRAVELLER -> Ticket.PARKING_FEE_TEMPOTRAVELLER;
        };
    }

    private int getDurationFromUI() {
        Toggle selectedToggle = durationGroup.getSelectedToggle();

        if (selectedToggle == null) {
            return 1;
        }

        Integer duration = (Integer) selectedToggle.getUserData();

        return duration != null ? duration : 1;
    }

    @FXML
    private void handleGenerateTicket() {
        if (!validateForm()) {
            return;
        }

        Ticket ticket = new Ticket();
        ticket.setCustomerName(customerNameField.getText().trim());
        ticket.setContactNumber(contactNumberField.getText().trim());
        ticket.setNumberOfPeople(safeInt(peopleSpinner, 1));
        ticket.setLifeJacketRequired(true);
        ticket.setLifeJacketCount(safeInt(peopleSpinner, 1));
        ticket.setRideDurationInHour(getDurationFromUI());
        ticket.setParkingRequired(parkingCheckBox.isSelected());

        if (parkingCheckBox.isSelected()
                && vehicleGroup.getSelectedToggle() != null) {
            ticket.setVehicleType(
                    (Ticket.VehicleType) vehicleGroup.getSelectedToggle().getUserData());
            ticket.setVehicleNumber(vehicleNumberField.getText().trim());
        }

        ticket.setBoatOwner(boatComboBox.getValue());
        ticket.setBookedBy(MainApp.loggedInUser);

        try {
            DatabaseManager.getInstance().saveTicket(
                    ticket,
                    boatComboBox.getValue().getId(),
                    MainApp.loggedInUser);
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("DB error: " + e.getMessage(), false);
            return;
        }

        try {
            String printerName = ThermalPrinterManager.printTicket(ticket);
            ThermalPrinterManager.printTicketToConsole(ticket);
            showStatus("Ticket sent to printer: " + printerName, true);
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("Print error: " + e.getMessage(), false);
        }
    }

    @FXML
    private void handleReset() {
        customerNameField.clear();
        contactNumberField.clear();

        peopleSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, MAX_PASSENGERS_WITHOUT_BOAT, 1));

        passengerCapacityValidationLabel.setText("");

        rb1Hour.setSelected(true);
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
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Choose folder to export Excel file");
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File directory = directoryChooser.showDialog(backupBtn.getScene().getWindow());

        if (directory == null) {
            return;
        }

        try {
            DatabaseManager.getInstance().exportToExcel(directory.getAbsolutePath());
            showStatus("Excel exported to: " + directory.getAbsolutePath(), true);
        } catch (Exception e) {
            showStatus("Export failed: " + e.getMessage(), false);
        }
    }

    @FXML
    private void handleLogout() {
        try {
            MainApp.loggedInUser = "";
            MainApp.loggedInRole = "";
            MainApp.showLogin((Stage) logoutBtn.getScene().getWindow());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewTickets() {
        try {
            List<String> tickets = DatabaseManager.getInstance().getTodaysTickets();

            if (tickets.isEmpty()) {
                showStatus("No tickets issued today.", false);
                return;
            }

            Stage stage = new Stage();
            stage.setTitle("Today's Tickets");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(viewTicketsBtn.getScene().getWindow());

            VBox vbox = new VBox(10);
            vbox.setPadding(new Insets(10));

            Label label = new Label("Tickets issued today:");

            TextArea textArea = new TextArea(String.join("\n", tickets));
            textArea.setEditable(false);
            textArea.setPrefRowCount(20);

            Button closeButton = new Button("Close");
            closeButton.setOnAction(e -> stage.close());

            vbox.getChildren().addAll(label, textArea, closeButton);

            Scene scene = new Scene(vbox, 600, 400);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            showStatus("Error fetching tickets: " + e.getMessage(), false);
        }
    }

    private boolean validateForm() {
        if (customerNameField.getText().isBlank()) {
            showStatus("Please enter customer name.", false);
            customerNameField.requestFocus();
            return false;
        }

        String phone = contactNumberField.getText().trim();

        if (phone.isBlank()) {
            showStatus("Please enter contact number.", false);
            contactNumberField.requestFocus();
            return false;
        }

        if (!phone.matches("\\d{10}")) {
            showStatus("Please enter a valid 10-digit contact number.", false);
            contactNumberField.requestFocus();
            return false;
        }

        if (boatComboBox.getValue() == null) {
            showStatus("Please select a boat / driver.", false);
            return false;
        }

        int passengers = safeInt(peopleSpinner, 1);
        int boatCapacity = boatComboBox.getValue().getCapacity();

        if (passengers > boatCapacity) {
            showStatus(
                    "Passengers cannot exceed the selected boat capacity of " + boatCapacity + ".",
                    false);
            peopleSpinner.requestFocus();
            return false;
        }

        if (parkingCheckBox.isSelected()
                && vehicleGroup.getSelectedToggle() == null) {
            showStatus("Please select a vehicle type.", false);
            return false;
        }

        return true;
    }

    private int safeInt(Spinner<Integer> spinner, int fallback) {
        try {
            Integer value = spinner.getValue();
            return value != null && value > 0 ? value : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void showStatus(String message, boolean success) {
        statusLabel.setText(message);
        statusLabel.setStyle(success
                ? "-fx-text-fill: #228b22; -fx-font-weight: bold;"
                : "-fx-text-fill: #c0392b; -fx-font-weight: bold;");

        FadeTransition fadeTransition =
                new FadeTransition(Duration.millis(300), statusLabel);

        fadeTransition.setFromValue(0);
        fadeTransition.setToValue(1);
        fadeTransition.play();
    }
}