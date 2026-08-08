package com.boatticket.controller;

import com.boatticket.MainApp;
import com.boatticket.db.DatabaseManager;
import com.boatticket.model.BoatOwner;
import com.boatticket.model.Ticket;
import com.boatticket.util.PdfTicketManager;
import com.boatticket.util.ThermalPrinterManager;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

    @FXML private TextField customerNameField;
    @FXML private TextField contactNumberField;
    @FXML private Label contactNumberValidationLabel;

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

    @FXML private TextField boatSearchField;
    @FXML private ComboBox<BoatOwner> boatComboBox;
    @FXML private Label boatDetailsLabel;

    @FXML private Label totalFeeLabel;
    @FXML private Label driverAmountLabel;
    @FXML private Label counterAmountLabel;
    @FXML private Label statusLabel;
    @FXML private Label loggedInLabel;

    @FXML private Button generateBtn;
    @FXML private Button resetBtn;
    @FXML private Button backupBtn;
    @FXML private Button viewTicketsBtn;
    @FXML private Button logoutBtn;

    @FXML private ToggleGroup durationGroup;

    private final ObservableList<BoatOwner> allBoats =
            FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        loggedInLabel.setText(
                "Logged in as:  " + MainApp.loggedInUser +
                        "  [" + MainApp.loggedInRole + "]"
        );

        configureContactNumberField();

        contactNumberField.textProperty().addListener((obs, oldValue, newValue) ->
                updateContactNumberValidationMessage());

        peopleSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1,
                        MAX_PASSENGERS_WITHOUT_BOAT,
                        1
                )
        );
        peopleSpinner.setEditable(true);
        configurePeopleSpinnerEditor();

        parkingBox.setVisible(false);
        parkingBox.setManaged(false);

        rbCar.setUserData(Ticket.VehicleType.CAR);
        rbBus.setUserData(Ticket.VehicleType.BUS);
        rbTempo.setUserData(Ticket.VehicleType.TEMPOTRAVELLER);

        rb1Hour.setUserData(1);
        rb2Hour.setUserData(2);
        rb1Hour.setSelected(true);

        loadBoats();

        boatSearchField.textProperty().addListener((obs, oldValue, term) -> {
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

        boatComboBox.setOnAction(event -> {
            BoatOwner selectedBoat = boatComboBox.getValue();

            if (selectedBoat != null) {
                boatDetailsLabel.setText(
                        "Number: " + selectedBoat.getId() +
                                "  | Driver: " + selectedBoat.getDriverName() +
                                "  |  Boat HR Number: " + selectedBoat.getBoatHRNumber() +
                                "  |  Boat HON Number: " + selectedBoat.getBoatHONumber() +
                                "  |  Jetty: " + selectedBoat.getJetty() +
                                "  |  Capacity: " + selectedBoat.getCapacity() +
                                "  |  Contact: " + selectedBoat.getContact()
                );

                updatePassengerCapacity();
            } else {
                boatDetailsLabel.setText("");
                resetPassengerCapacity();
            }

            passengerCapacityValidationLabel.setText("");
        });

        peopleSpinner.valueProperty().addListener((obs, oldValue, newValue) -> refreshFees());
        durationGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> refreshFees());

        parkingCheckBox.selectedProperty().addListener((obs, oldValue, selected) -> {
            parkingBox.setVisible(selected);
            parkingBox.setManaged(selected);
            refreshFees();
        });

        vehicleGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> refreshFees());

        refreshFees();
    }

    private void configureContactNumberField() {
        UnaryOperator<TextFormatter.Change> contactNumberFilter = change -> {
            String newValue = change.getControlNewText();
            return newValue.matches("\\d{0,10}") ? change : null;
        };

        contactNumberField.setTextFormatter(
                new TextFormatter<>(contactNumberFilter)
        );
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
                            "Maximum passengers allowed: " + selectedBoat.getCapacity()
                    );
                    return null;
                }

                passengerCapacityValidationLabel.setText("");
                return change;
            } catch (NumberFormatException e) {
                return null;
            }
        };

        peopleSpinner.getEditor().setTextFormatter(
                new TextFormatter<>(passengerFilter)
        );
    }

    private void updateContactNumberValidationMessage() {
        String phone = contactNumberField.getText().trim();

        if (!phone.isBlank() && phone.length() < 10) {
            contactNumberValidationLabel.setText(
                    "Enter 10-digit contact number."
            );
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

        peopleSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1,
                        maximumCapacity,
                        allowedPassengers
                )
        );

        refreshFees();
    }

    private void resetPassengerCapacity() {
        int currentPassengers = safeInt(peopleSpinner, 1);
        int allowedPassengers = Math.min(
                currentPassengers,
                MAX_PASSENGERS_WITHOUT_BOAT
        );

        peopleSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1,
                        MAX_PASSENGERS_WITHOUT_BOAT,
                        allowedPassengers
                )
        );

        refreshFees();
    }

    private void loadBoats() {
        try (ResultSet resultSet = DatabaseManager.getInstance().getAllBoats()) {
            while (resultSet.next()) {
                allBoats.add(new BoatOwner(
                        resultSet.getInt("id"),
                        resultSet.getString("driver_name"),
                        resultSet.getString("boat_hr_number"),
                        resultSet.getString("boat_hon_number"),
                        resultSet.getString("jetty"),
                        resultSet.getInt("capacity"),
                        resultSet.getString("contact")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        boatComboBox.setItems(allBoats);
    }

    private void refreshFees() {
        int people = safeInt(peopleSpinner, 1);
        int duration = getDurationFromUI();

        int boatFee = duration == 2
                ? Ticket.BOAT_RIDE_FEE_2HOURS
                : Ticket.BOAT_RIDE_FEE_PER_HOUR * duration;

        int jacketFee = Ticket.LIFE_JACKET_FEE * people;
        int parkingFee = getParkingFeeFromUI();
        int amountToDriver = boatFee + jacketFee;
        int total = amountToDriver + parkingFee;

        boatFeeLabel.setText("Rs " + boatFee);
        parkingFeeLabel.setText("Rs " + parkingFee);
        driverAmountLabel.setText("Rs " + amountToDriver);
        counterAmountLabel.setText("Rs " + parkingFee);
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
                    (Ticket.VehicleType) vehicleGroup.getSelectedToggle().getUserData()
            );
            ticket.setVehicleNumber(vehicleNumberField.getText().trim());
        }

        ticket.setBoatOwner(boatComboBox.getValue());
        ticket.setBookedBy(MainApp.loggedInUser);

        try {
            DatabaseManager.getInstance().saveTicket(
                    ticket,
                    boatComboBox.getValue().getId(),
                    MainApp.loggedInUser
            );
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("DB error: " + e.getMessage(), false);
            return;
        }

        generatePdfAndPrint(ticket);
    }

    @FXML
    private void handleReset() {
        customerNameField.clear();
        contactNumberField.clear();

        peopleSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1,
                        MAX_PASSENGERS_WITHOUT_BOAT,
                        1
                )
        );

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
        directoryChooser.setInitialDirectory(
                new File(System.getProperty("user.home"))
        );

        File directory = directoryChooser.showDialog(
                backupBtn.getScene().getWindow()
        );

        if (directory == null) {
            return;
        }

        try {
            DatabaseManager.getInstance().exportToExcel(
                    directory.getAbsolutePath()
            );
            showStatus(
                    "Excel exported to: " + directory.getAbsolutePath(),
                    true
            );
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
            List<Ticket> tickets = DatabaseManager.getInstance().getTodaysTickets();

            if (tickets.isEmpty()) {
                showStatus("No tickets issued today.", false);
                return;
            }

            Stage stage = new Stage();
            stage.setTitle("Today's Tickets");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(viewTicketsBtn.getScene().getWindow());

            VBox ticketRows = new VBox(8);
            ticketRows.setPadding(new Insets(10));

            for (Ticket ticket : tickets) {
                ticketRows.getChildren().add(createTicketRow(ticket));
            }

            ScrollPane scrollPane = new ScrollPane(ticketRows);
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefViewportHeight(360);

            Button closeButton = new Button("Close");
            closeButton.setOnAction(event -> stage.close());

            VBox root = new VBox(10);
            root.setPadding(new Insets(12));
            root.getChildren().addAll(
                    new Label("Tickets issued today:"),
                    scrollPane,
                    closeButton
            );

            Scene scene = new Scene(root, 950, 460);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            showStatus("Error fetching tickets: " + e.getMessage(), false);
        }
    }

    private HBox createTicketRow(Ticket ticket) {
        String driverName = ticket.getBoatOwner() != null
                ? ticket.getBoatOwner().getDriverName()
                : "Boat details unavailable";

        Label ticketDetails = new Label(
                "Customer: " + ticket.getCustomerName() +
                        "  |  Ticket ID: " + ticket.getTicketId() +
                        "  |  Time: " + ticket.getFormattedBookingTime() +
                        "  |  Driver: " + driverName
        );

        ticketDetails.setWrapText(true);
        ticketDetails.setMaxWidth(Double.MAX_VALUE);

        Button reprintButton = new Button("Reprint");
        reprintButton.setOnAction(event -> reprintTicket(ticket));

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        row.setStyle(
                "-fx-background-color: white;" +
                        "-fx-border-color: #d5dce5;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;"
        );

        HBox.setHgrow(ticketDetails, Priority.ALWAYS);
        row.getChildren().addAll(ticketDetails, reprintButton);

        return row;
    }

    private void reprintTicket(Ticket ticket) {
        generatePdfAndPrint(ticket);
    }

    /**
     * Generates the ticket PDF and saves it to the Downloads/BoatTickets
     * folder (it is NOT opened automatically). Also attempts to send the
     * ticket to the thermal/system printer as a best-effort secondary step
     * so a missing or misconfigured printer never blocks the PDF.
     */
    private void generatePdfAndPrint(Ticket ticket) {
        File pdfFile;

        try {
            pdfFile = PdfTicketManager.generateTicketPdf(ticket);
            showStatus(
                    "Ticket " + ticket.getTicketId() + " saved to: " + pdfFile.getAbsolutePath(),
                    true
            );
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("Failed to create PDF ticket: " + e.getMessage(), false);
            return;
        }

        try {
            ThermalPrinterManager.printTicket(ticket);
            ThermalPrinterManager.printTicketToConsole(ticket);
        } catch (Exception e) {
            // Thermal printing is best-effort only; do not block on it or
            // overwrite the PDF status message with a printer error.
            e.printStackTrace();
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
                    "Passengers cannot exceed the selected boat capacity of " +
                            boatCapacity + ".",
                    false
            );
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