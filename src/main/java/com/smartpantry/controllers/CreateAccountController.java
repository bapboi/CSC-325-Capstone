package com.smartpantry.controllers;

import com.smartpantry.data.AppData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class CreateAccountController {

    @FXML
    private TextField nameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    @FXML
    private void handleCreateAccount(ActionEvent event) {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Complete all fields before creating an account.");
            return;
        }

        // Temporary account creation until Firebase authentication is connected.
        AppData.currentUserEmail = email;
        Navigation.goTo(event, "Pantry.fxml");
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        Navigation.goTo(event, "Login.fxml");
    }
}
