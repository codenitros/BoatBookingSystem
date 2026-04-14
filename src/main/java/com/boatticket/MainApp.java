package com.boatticket;

import com.boatticket.db.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    public static String loggedInUser = "";
    public static String loggedInRole = "";

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialise DB on startup
        DatabaseManager.getInstance();

        showLogin(primaryStage);
    }

    public static void showLogin(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/com/boatticket/login.fxml"));
        Scene scene = new Scene(loader.load(), 420, 380);
        scene.getStylesheets().add(
                MainApp.class.getResource("/com/boatticket/styles.css").toExternalForm());
        stage.setTitle("Sharavati Boat Ticket System  —  Login");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void showBooking(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/com/boatticket/booking.fxml"));
        Scene scene = new Scene(loader.load(), 860, 720);
        scene.getStylesheets().add(
                MainApp.class.getResource("/com/boatticket/styles.css").toExternalForm());
        stage.setTitle("Sharavati Boat Ride Ticket Booking  —  " + loggedInUser);
        stage.setScene(scene);
        stage.setMinWidth(860);
        stage.setMinHeight(720);
        stage.setResizable(true);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
