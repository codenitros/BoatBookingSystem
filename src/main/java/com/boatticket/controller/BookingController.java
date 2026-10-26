package com.boatticket.controller;

import com.boatticket.MainApp;
import com.boatticket.config.ConfigManager;
import com.boatticket.db.DatabaseManager;
import com.boatticket.model.BoatOwner;
import com.boatticket.model.Ticket;
import com.boatticket.util.PdfTicketManager;
import com.boatticket.util.ThermalPrinterManager;
import java.io.File;
import java.net.URL;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.Pair;

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
  @FXML private Label tenantTitleLabel;

  @FXML private Button generateBtn;
  @FXML private Button resetBtn;
  @FXML private Button backupBtn;
  @FXML private Button viewTicketsBtn;
  @FXML private Button logoutBtn;

  @FXML private ToggleGroup durationGroup;

  private final ObservableList<BoatOwner> allBoats = FXCollections.observableArrayList();

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    tenantTitleLabel.setText("🚤 " + ConfigManager.getApplicationName());
    loggedInLabel.setText(
        "Logged in as:  " + MainApp.loggedInUser + "  [" + MainApp.loggedInRole + "]");

    configureContactNumberField();

    contactNumberField
        .textProperty()
        .addListener((obs, oldValue, newValue) -> updateContactNumberValidationMessage());

    peopleSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(1, MAX_PASSENGERS_WITHOUT_BOAT, 1));
    peopleSpinner.setEditable(true);
    configurePeopleSpinnerEditor();

    parkingBox.setVisible(false);
    parkingBox.setManaged(false);

    rbCar.setUserData(Ticket.VehicleType.CAR);
    rbBus.setUserData(Ticket.VehicleType.BUS);
    rbTempo.setUserData(Ticket.VehicleType.TEMPOTRAVELLER);
    updateVehicleFeeLabels();

    rb1Hour.setUserData(1);
    rb2Hour.setUserData(2);
    rb1Hour.setSelected(true);

    loadBoats();

    boatSearchField
        .textProperty()
        .addListener(
            (obs, oldValue, term) -> {
              String query = term.trim();
              String lower = query.toLowerCase();
              ObservableList<BoatOwner> filtered = FXCollections.observableArrayList();

              // A purely numeric search (e.g. "40") is treated as an exact
              // boat-ID lookup, NOT a substring match. Substring-matching a
              // number against HR/HON registration numbers was the bug --
              // searching "40" matched boat #7 (HR number "640" contains
              // "40"), boat #73 (HON number "HON0040" contains "40"), etc.,
              // none of which are boat #40 at all.
              boolean isNumericQuery =
                  !query.isEmpty() && query.chars().allMatch(Character::isDigit);

              for (BoatOwner boat : allBoats) {
                boolean matches;

                if (isNumericQuery) {
                  matches = String.valueOf(boat.getId()).equals(query);
                } else {
                  matches =
                      boat.getDriverName().toLowerCase().contains(lower)
                          || boat.getBoatHRNumber().toLowerCase().contains(lower)
                          || boat.getBoatHONumber().toLowerCase().contains(lower)
                          || boat.getJetty().toLowerCase().contains(lower);
                }

                if (matches) {
                  filtered.add(boat);
                }
              }

              boatComboBox.setItems(filtered);

              if (!filtered.isEmpty()) {
                boatComboBox.show();
              }
            });

    boatComboBox.setOnAction(
        event -> {
          BoatOwner selectedBoat = boatComboBox.getValue();

          if (selectedBoat != null) {
            boatDetailsLabel.setText(
                "Number: "
                    + selectedBoat.getId()
                    + "  | Driver: "
                    + selectedBoat.getDriverName()
                    + "  |  Boat HR Number: "
                    + selectedBoat.getBoatHRNumber()
                    + "  |  Boat HON Number: "
                    + selectedBoat.getBoatHONumber()
                    + "  |  Jetty: "
                    + selectedBoat.getJetty()
                    + "  |  Capacity: "
                    + selectedBoat.getCapacity()
                    + "  |  Contact: "
                    + selectedBoat.getContact());

            updatePassengerCapacity();
          } else {
            boatDetailsLabel.setText("");
            resetPassengerCapacity();
          }

          passengerCapacityValidationLabel.setText("");
        });

    peopleSpinner.valueProperty().addListener((obs, oldValue, newValue) -> refreshFees());
    durationGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> refreshFees());

    parkingCheckBox
        .selectedProperty()
        .addListener(
            (obs, oldValue, selected) -> {
              parkingBox.setVisible(selected);
              parkingBox.setManaged(selected);
              refreshFees();
            });

    vehicleGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> refreshFees());

    refreshFees();
  }

  private void configureContactNumberField() {
    UnaryOperator<TextFormatter.Change> contactNumberFilter =
        change -> {
          String newValue = change.getControlNewText();
          return newValue.matches("\\d{0,10}") ? change : null;
        };

    contactNumberField.setTextFormatter(new TextFormatter<>(contactNumberFilter));
  }

  private void configurePeopleSpinnerEditor() {
    UnaryOperator<TextFormatter.Change> passengerFilter =
        change -> {
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

    peopleSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maximumCapacity, allowedPassengers));

    refreshFees();
  }

  private void resetPassengerCapacity() {
    int currentPassengers = safeInt(peopleSpinner, 1);
    int allowedPassengers = Math.min(currentPassengers, MAX_PASSENGERS_WITHOUT_BOAT);

    peopleSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            1, MAX_PASSENGERS_WITHOUT_BOAT, allowedPassengers));

    refreshFees();
  }

  private void loadBoats() {
    try (ResultSet resultSet = DatabaseManager.getInstance().getAllBoats()) {
      while (resultSet.next()) {
        allBoats.add(
            new BoatOwner(
                resultSet.getInt("id"),
                resultSet.getString("driver_name"),
                resultSet.getString("boat_hr_number"),
                resultSet.getString("boat_hon_number"),
                resultSet.getString("jetty"),
                resultSet.getInt("capacity"),
                resultSet.getString("contact")));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    boatComboBox.setItems(allBoats);
  }

  private void refreshFees() {
    updateVehicleFeeLabels();

    int people = safeInt(peopleSpinner, 1);
    int duration = getDurationFromUI();

    int boatFee =
        duration == 2
            ? ConfigManager.getBoatRideFee2Hours()
            : ConfigManager.getBoatRideFeePerHour() * duration;

    int jacketFee = ConfigManager.getLifeJacketFee() * people;
    int parkingFee = getParkingFeeFromUI();
    int amountToDriver = boatFee + jacketFee;
    int total = amountToDriver + parkingFee;

    boatFeeLabel.setText("Rs " + boatFee);
    parkingFeeLabel.setText("Rs " + parkingFee);
    driverAmountLabel.setText("Rs " + amountToDriver);
    counterAmountLabel.setText("Rs " + parkingFee);
    totalFeeLabel.setText("Rs " + total);
  }

  private void updateVehicleFeeLabels() {
    rbCar.setText("Car (Rs " + ConfigManager.getParkingFeeCar() + ")");
    rbBus.setText("Bus (Rs " + ConfigManager.getParkingFeeBus() + ")");
    rbTempo.setText("Tempo Traveller (Rs " + ConfigManager.getParkingFeeTempoTraveller() + ")");
  }

  private int getParkingFeeFromUI() {
    if (!parkingCheckBox.isSelected()) {
      return 0;
    }

    Toggle selectedToggle = vehicleGroup.getSelectedToggle();

    if (selectedToggle == null) {
      return 0;
    }

    Ticket.VehicleType vehicleType = (Ticket.VehicleType) selectedToggle.getUserData();

    if (vehicleType == null) {
      return 0;
    }

    return switch (vehicleType) {
      case CAR -> ConfigManager.getParkingFeeCar();
      case BUS -> ConfigManager.getParkingFeeBus();
      case TEMPOTRAVELLER -> ConfigManager.getParkingFeeTempoTraveller();
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

    if (parkingCheckBox.isSelected() && vehicleGroup.getSelectedToggle() != null) {
      ticket.setVehicleType((Ticket.VehicleType) vehicleGroup.getSelectedToggle().getUserData());
      ticket.setVehicleNumber(vehicleNumberField.getText().trim());
    }

    ticket.setBoatOwner(boatComboBox.getValue());
    ticket.setBookedBy(MainApp.loggedInUser);

    try {
      DatabaseManager.getInstance()
          .saveTicket(ticket, boatComboBox.getValue().getId(), MainApp.loggedInUser);
    } catch (Exception e) {
      e.printStackTrace();
      showStatus("DB error: " + e.getMessage(), false);
      return;
    }

    printTicketOnly(ticket);

    // Ticket is saved and printing has been attempted either way --
    // clear the form so the operator can start the next customer
    // right away. The print status message above is left in place
    // (resetFormFields does not touch statusLabel) so success/failure
    // is still visible after the reset.
    resetFormFields();
  }

  @FXML
  private void handleReset() {
    resetFormFields();
    statusLabel.setText("");
  }

  /**
   * Clears every input field back to its default state, without touching statusLabel -- used both
   * by the Reset button (which also clears the status message itself) and automatically after a
   * ticket is printed (which wants to keep showing the print result).
   */
  private void resetFormFields() {
    customerNameField.clear();
    contactNumberField.clear();

    peopleSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(1, MAX_PASSENGERS_WITHOUT_BOAT, 1));

    passengerCapacityValidationLabel.setText("");

    rb1Hour.setSelected(true);
    resetParkingFields();
    boatSearchField.clear();
    boatComboBox.setValue(null);
    boatComboBox.setItems(allBoats);
    boatDetailsLabel.setText("");

    refreshFees();
  }

  /**
   * Restores every parking control, including the parking form visibility, to its initial state.
   */
  private void resetParkingFields() {
    parkingCheckBox.setSelected(false);
    parkingBox.setVisible(false);
    parkingBox.setManaged(false);
    vehicleGroup.selectToggle(null);
    vehicleNumberField.clear();
  }

  @FXML
  private void handleBackup() {
    Optional<Pair<LocalDate, LocalDate>> dateRange = showDateRangeDialog();

    if (dateRange.isEmpty()) {
      return;
    }

    LocalDate fromDate = dateRange.get().getKey();
    LocalDate toDate = dateRange.get().getValue();

    DirectoryChooser directoryChooser = new DirectoryChooser();
    directoryChooser.setTitle("Choose folder to save the ticket report PDF");
    directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));

    File directory = directoryChooser.showDialog(backupBtn.getScene().getWindow());

    if (directory == null) {
      return;
    }

    try {
      File exportedFile =
          DatabaseManager.getInstance()
              .exportToPdfReport(directory.getAbsolutePath(), fromDate, toDate);
      showStatus("Report saved (password protected) to: " + exportedFile.getAbsolutePath(), true);
    } catch (Exception e) {
      e.printStackTrace();

      // Build the full cause chain so the real root exception is
      // visible even if it's wrapped by another exception.
      StringBuilder details = new StringBuilder();
      Throwable current = e;
      while (current != null) {
        details
            .append(current.getClass().getSimpleName())
            .append(": ")
            .append(current.getMessage())
            .append("\n");
        current = current.getCause();
      }

      showStatus("Export failed — see popup for details.", false);

      Alert alert = new Alert(Alert.AlertType.ERROR);
      alert.setTitle("Export Failed");
      alert.setHeaderText("Could not generate the PDF report");
      alert.setContentText(details.toString());
      alert.showAndWait();
    }
  }

  /**
   * Shows a dialog with two calendar (DatePicker) controls for a custom date range, plus
   * quick-range buttons (1 Day / 3 Days / 1 Week / 1 Month) that immediately export using a rolling
   * range ending today.
   *
   * @return the chosen date range, or an empty Optional if cancelled
   */
  private Optional<Pair<LocalDate, LocalDate>> showDateRangeDialog() {
    Dialog<Pair<LocalDate, LocalDate>> dialog = new Dialog<>();
    dialog.setTitle("Export Ticket Report (PDF)");
    dialog.setHeaderText("Pick a custom date range, or use a quick option below");

    ButtonType exportButtonType = new ButtonType("Export", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, ButtonType.CANCEL);

    LocalDate today = LocalDate.now();
    LocalDate firstOfMonth = today.withDayOfMonth(1);

    DatePicker fromPicker = new DatePicker(firstOfMonth);
    DatePicker toPicker = new DatePicker(today);
    fromPicker.setPromptText("dd-MM-yyyy");
    toPicker.setPromptText("dd-MM-yyyy");

    Label validationLabel = new Label();
    validationLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");

    GridPane grid = new GridPane();
    grid.setHgap(12);
    grid.setVgap(12);
    grid.setPadding(new Insets(20, 20, 4, 10));
    grid.add(new Label("From Date:"), 0, 0);
    grid.add(fromPicker, 1, 0);
    grid.add(new Label("To Date:"), 0, 1);
    grid.add(toPicker, 1, 1);
    grid.add(validationLabel, 0, 2, 2, 1);

    Label quickLabel = new Label("Quick ranges (exports immediately):");
    quickLabel.setStyle("-fx-font-weight: bold; -fx-padding: 6 0 0 0;");

    Button oneDayBtn = new Button("1 Day");
    Button threeDaysBtn = new Button("3 Days");
    Button oneWeekBtn = new Button("1 Week");
    Button oneMonthBtn = new Button("1 Month");

    HBox quickButtons = new HBox(8, oneDayBtn, threeDaysBtn, oneWeekBtn, oneMonthBtn);

    VBox content = new VBox(10, grid, quickLabel, quickButtons);
    content.setPadding(new Insets(4, 20, 12, 20));

    dialog.getDialogPane().setContent(content);

    // Quick-range buttons set the result and close the dialog right
    // away, without needing the "Export" button.
    oneDayBtn.setOnAction(
        event -> {
          dialog.setResult(new Pair<>(today, today));
          dialog.close();
        });
    threeDaysBtn.setOnAction(
        event -> {
          dialog.setResult(new Pair<>(today.minusDays(2), today));
          dialog.close();
        });
    oneWeekBtn.setOnAction(
        event -> {
          dialog.setResult(new Pair<>(today.minusDays(6), today));
          dialog.close();
        });
    oneMonthBtn.setOnAction(
        event -> {
          dialog.setResult(new Pair<>(today.minusDays(29), today));
          dialog.close();
        });

    Node exportButton = dialog.getDialogPane().lookupButton(exportButtonType);
    exportButton.addEventFilter(
        ActionEvent.ACTION,
        event -> {
          LocalDate from = fromPicker.getValue();
          LocalDate to = toPicker.getValue();

          if (from == null || to == null) {
            validationLabel.setText("Please select both a 'From' and 'To' date.");
            event.consume();
          } else if (from.isAfter(to)) {
            validationLabel.setText("'From' date cannot be after the 'To' date.");
            event.consume();
          } else if (to.isAfter(today)) {
            validationLabel.setText("'To' date cannot be in the future.");
            event.consume();
          }
        });

    dialog.setResultConverter(
        buttonType -> {
          if (buttonType == exportButtonType) {
            return new Pair<>(fromPicker.getValue(), toPicker.getValue());
          }
          return null;
        });

    return dialog.showAndWait();
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
      root.getChildren().addAll(new Label("Tickets issued today:"), scrollPane, closeButton);

      Scene scene = new Scene(root, 950, 460);
      stage.setScene(scene);
      stage.showAndWait();
    } catch (Exception e) {
      showStatus("Error fetching tickets: " + e.getMessage(), false);
    }
  }

  private HBox createTicketRow(Ticket ticket) {
    String driverName =
        ticket.getBoatOwner() != null
            ? ticket.getBoatOwner().getDriverName()
            : "Boat details unavailable";

    Label ticketDetails =
        new Label(
            "Driver SL No: "
                + ticket.getBoatOwner().getId()
                + "  |  Driver Name: "
                + ticket.getBoatOwner().getDriverName()
                + "  |  Customer: "
                + ticket.getCustomerName()
                + "  |  Ticket ID: "
                + ticket.getTicketId()
                + "  |  Time: "
                + ticket.getFormattedBookingTime());

    ticketDetails.setWrapText(true);
    ticketDetails.setMaxWidth(Double.MAX_VALUE);

    Button reprintButton = new Button("Reprint");
    reprintButton.setOnAction(event -> reprintTicket(ticket));

    HBox row = new HBox(12);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(8));
    row.setStyle(
        "-fx-background-color: white;"
            + "-fx-border-color: #d5dce5;"
            + "-fx-border-radius: 4;"
            + "-fx-background-radius: 4;");

    HBox.setHgrow(ticketDetails, Priority.ALWAYS);
    row.getChildren().addAll(ticketDetails, reprintButton);

    return row;
  }

  private void reprintTicket(Ticket ticket) {
    printToThermalAndMaybePdf(ticket, "Reprinting");
  }

  /** Used by the main "Print" button. Sends the ticket straight to the thermal/system printer. */
  private void printTicketOnly(Ticket ticket) {
    printToThermalAndMaybePdf(ticket, "Printing");
  }

  /**
   * Shared by both the main "Print" button and the "Reprint" button (from the Today's Tickets
   * list). Always sends the ticket to the thermal/system printer. Whether a reference PDF also gets
   * generated is controlled ENTIRELY by {@link ConfigManager#isDevMode()} (backed by the external
   * Config.properties) — there is no other path in this class that creates a PDF, so this one flag
   * governs both buttons:
   *
   * <p>- dev.mode=true (development machine): a reference PDF is also saved to
   * Downloads/BoatTickets, best-effort, purely so we can check the output. It is never opened
   * automatically and never blocks or fails the thermal print. - dev.mode=false (deployed/client
   * machine): no PDF is generated under any circumstance — thermal print only, for both Print and
   * Reprint.
   *
   * <p>Shows an immediate "printing" status message before attempting the print job, then updates
   * it with the result.
   *
   * @param ticket the ticket to print
   * @param actionLabel verb shown in the status message, e.g. "Printing" or "Reprinting"
   */
  private void printToThermalAndMaybePdf(Ticket ticket, String actionLabel) {
    showStatus(actionLabel + " ticket " + ticket.getTicketId() + " to thermal printer...", true);

    // Dev-only reference PDF — controlled by Config.properties (dev.mode).
    // Best-effort: never blocks or overrides the thermal print result,
    // and never runs at all when dev.mode=false.
    if (ConfigManager.isDevMode()) {
      try {
        File referencePdf = PdfTicketManager.generateTicketPdf(ticket);
        System.out.println("[DEV MODE] Reference PDF saved to: " + referencePdf.getAbsolutePath());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    try {
      ThermalPrinterManager.printTicket(ticket);
      ThermalPrinterManager.printTicketToConsole(ticket);
      showStatus("Ticket " + ticket.getTicketId() + " is printing on the thermal printer.", true);
    } catch (Exception e) {
      // Full technical detail (which printer, why it was rejected,
      // list of available printers, etc.) goes to the console only
      // — for us to debug. The operator just needs to know the
      // print didn't go through and to check the printer.
      System.err.println(
          "Thermal print failed for ticket " + ticket.getTicketId() + ": " + e.getMessage());
      e.printStackTrace();
      showStatus(
          "Could not print ticket "
              + ticket.getTicketId()
              + ". Please check the thermal printer and try again.",
          false);
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
          "Passengers cannot exceed the selected boat capacity of " + boatCapacity + ".", false);
      peopleSpinner.requestFocus();
      return false;
    }

    if (parkingCheckBox.isSelected() && vehicleGroup.getSelectedToggle() == null) {
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
    statusLabel.setStyle(
        success
            ? "-fx-text-fill: #228b22; -fx-font-weight: bold;"
            : "-fx-text-fill: #c0392b; -fx-font-weight: bold;");

    FadeTransition fadeTransition = new FadeTransition(Duration.millis(300), statusLabel);

    fadeTransition.setFromValue(0);
    fadeTransition.setToValue(1);
    fadeTransition.play();
  }
}
