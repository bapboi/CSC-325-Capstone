package com.smartpantry.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

import java.io.IOException;

public class SettingsController {

    @FXML
    private CheckBox notificationsCheckBox;

    @FXML
    private ComboBox<String> themeComboBox;

    @FXML
    private void initialize() {
        notificationsCheckBox.setSelected(true);
        themeComboBox.getItems().addAll("Light", "Dark", "System Default");
        themeComboBox.getSelectionModel().select("Light");
    }

    @FXML
    private void handleSaveSettings() {
        String notifications = notificationsCheckBox.isSelected() ? "on" : "off";
        String theme = themeComboBox.getValue();

        showInfo("Settings Saved", "Notifications: " + notifications + "\nTheme: " + theme);
    }

    @FXML
    private void handleBackToPantry() {
        try {
            Nav.go((Stage) notificationsCheckBox.getScene().getWindow(), Nav.Screen.PANTRY);
        } catch (IOException e) {
            showInfo("Navigation Error", "Failed to load pantry screen: " + e.getMessage());
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}