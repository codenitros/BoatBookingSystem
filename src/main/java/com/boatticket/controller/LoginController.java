package com.boatticket.controller;

import com.boatticket.MainApp;
import com.boatticket.db.DatabaseManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginBtn;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            errorLabel.setText("Please enter username and password.");
            return;
        }

        String role = DatabaseManager.getInstance().authenticate(username, password);
        if (role != null) {
            MainApp.loggedInUser = username;
            MainApp.loggedInRole = role;
            try {
                // Get stage from any available node
                Stage stage = (Stage) usernameField.getScene().getWindow();
                MainApp.showBooking(stage);
            } catch (Exception e) {
                e.printStackTrace();
                errorLabel.setText("Error loading booking screen: " + e.getMessage());
            }
        } else {
            errorLabel.setText("Invalid username or password.");
            passwordField.clear();
            passwordField.requestFocus();
        }
    }

    @FXML
    private void handleKeyPress(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) handleLogin();
    }
}
