package com.smartpantry.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;

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
        Navigation.showInfo("Settings Saved", "Notifications: " + notifications + "\nTheme: " + theme);
    }

    @FXML
    private void handleBackToPantry(ActionEvent event) {
        Navigation.goTo(event, "Pantry.fxml");
    }
}
