package com.boatticket;

import com.boatticket.backup.DailyBackupService;
import com.boatticket.config.ConfigManager;
import com.boatticket.db.DatabaseManager;
import com.boatticket.update.UpdateInfo;
import com.boatticket.update.UpdateService;
import java.awt.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MainApp extends Application {

  public static String loggedInUser = "";
  public static String loggedInRole = "";
  private final UpdateService updateService = new UpdateService();

  @Override
  public void start(Stage primaryStage) throws Exception {
    // Initialise DB on startup
    DatabaseManager.getInstance();
    DailyBackupService.runIfDueAsync();
    Image image = new Image("icons8-boat-48.png");
    primaryStage.getIcons().add(image);
    showLogin(primaryStage);
    updateService.checkForUpdateAsync(update -> Platform.runLater(() -> offerUpdate(update)));
  }

  public static void showLogin(Stage stage) throws Exception {
    FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/boatticket/login.fxml"));
    Scene scene = new Scene(loader.load(), 800, 400);
    scene
        .getStylesheets()
        .add(MainApp.class.getResource("/com/boatticket/styles.css").toExternalForm());
    stage.setTitle(ConfigManager.getApplicationName() + "  —  Login");
    stage.setScene(scene);
    stage.setResizable(false);
    stage.show();
  }

  public static void showBooking(Stage stage) throws Exception {
    FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/boatticket/booking.fxml"));
    Scene scene = new Scene(loader.load(), 860, 720);
    scene
        .getStylesheets()
        .add(MainApp.class.getResource("/com/boatticket/styles.css").toExternalForm());
    stage.setTitle(ConfigManager.getApplicationName() + "  —  " + loggedInUser);
    stage.setScene(scene);
    stage.setMinWidth(860);
    stage.setMinHeight(720);
    stage.setResizable(true);
    stage.setMaximized(true);
    stage.show();
  }

  private void offerUpdate(UpdateInfo update) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Update available");
    alert.setHeaderText("Boat Booking System " + update.version() + " is available");
    alert.setContentText("Download and install the update now? The application will close first.");
    ButtonType updateNow = new ButtonType("Update and install");
    ButtonType later = new ButtonType("Later");
    alert.getButtonTypes().setAll(updateNow, later);

    if (alert.showAndWait().orElse(later) == updateNow) {
      updateService.downloadAndInstallAsync(
          update,
          () -> Platform.runLater(Platform::exit),
          failure ->
              Platform.runLater(
                  () -> {
                    Alert failureAlert = new Alert(Alert.AlertType.ERROR);
                    failureAlert.setTitle("Update failed");
                    failureAlert.setHeaderText("The update could not be installed");
                    failureAlert.setContentText(failure.getMessage());
                    failureAlert.showAndWait();
                  }));
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
