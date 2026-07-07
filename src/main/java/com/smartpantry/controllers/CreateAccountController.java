package com.smartpantry.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

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

        // Temporary account creation until Firebase authentication is connected here.
        statusLabel.setText("Account created temporarily.");

        goTo(Nav.Screen.PANTRY);
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        goTo(Nav.Screen.LOGIN);
    }

    private void goTo(Nav.Screen screen) {
        try {
            Nav.go((Stage) emailField.getScene().getWindow(), screen);
        } catch (IOException e) {
            statusLabel.setText("Failed to load screen: " + e.getMessage());
        }
    }
}