package com.smartpantry.controllers;

import com.smartpantry.data.AppData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class ProfileController {

    @FXML
    private Label emailLabel;

    @FXML
    private Label pantryCountLabel;

    @FXML
    private Label savedRecipeCountLabel;

    @FXML
    private void initialize() {
        emailLabel.setText(AppData.currentUserEmail);
        pantryCountLabel.setText(String.valueOf(AppData.PANTRY_ITEMS.size()));
        savedRecipeCountLabel.setText(String.valueOf(AppData.SAVED_RECIPES.size()));
    }

    @FXML
    private void handleSettings(ActionEvent event) {
        Navigation.goTo(event, "Settings.fxml");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        Navigation.goTo(event, "Login.fxml");
    }

    @FXML
    private void handleBackToPantry(ActionEvent event) {
        Navigation.goTo(event, "Pantry.fxml");
    }
}
