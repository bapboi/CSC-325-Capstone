package com.smartpantry.controllers;

import com.smartpantry.services.AuthService;
import com.smartpantry.services.Session;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.io.IOException;

public class LoginController {

  @FXML private TextField emailField;
  @FXML private PasswordField passwordField;
  @FXML private Label statusLabel;
  @FXML private Button loginButton;

  private final AuthService authService = new AuthService();

  @FXML
  private void onLogin() {
    String email = emailField.getText();
    String password = passwordField.getText();

    if (email == null || email.isBlank() || password == null || password.isBlank()) {
      setStatus("Enter an email and password", false);
      return;
    }

    loginButton.setDisable(true);
    setStatus("Signing in...", true);

    Task<AuthService.AuthResult> task = new Task<>() {
      @Override
      protected AuthService.AuthResult call() throws Exception {
        return authService.signIn(email, password);
      }
    };

    task.setOnSucceeded(e -> {
      AuthService.AuthResult result = task.getValue();
      Session.getInstance().setUser(result.uid(), result.email());
      Platform.runLater(() -> {
        try {
          Nav.go((Stage) emailField.getScene().getWindow(), Nav.Screen.PANTRY);
        } catch (IOException ex) {
          setStatus("Failed to load main screen: " + ex.getMessage(), false);
        }
      });
    });

    task.setOnFailed(e -> {
      setStatus(task.getException().getMessage(), false);
      loginButton.setDisable(false);
    });

    new Thread(task, "auth-request").start();
  }

  @FXML
  private void onCreateAccountToggle() {
    try {
      Nav.go((Stage) emailField.getScene().getWindow(), Nav.Screen.CREATE_ACCOUNT);
    } catch (IOException e) {
      setStatus("Failed to load screen: " + e.getMessage(), false);
    }
  }

  private void setStatus(String message, boolean ok) {
    Platform.runLater(() -> {
      statusLabel.setText(message);
      statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    });
  }
}
