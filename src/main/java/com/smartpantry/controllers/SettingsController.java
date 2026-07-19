package com.smartpantry.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class SettingsController {

    @FXML
    public void initialize() {
        // No setup is needed for this screen right now.
    }

    @FXML
    private void onChangePassword() {
        showNotImplementedMessage();
    }

    @FXML
    private void onDeleteAccount() {
        showNotImplementedMessage();
    }

    @FXML
    private void onBackToProfile() {
        nav(Nav.Screen.PROFILE);
    }

    @FXML
    private void onNavPantry() {
        nav(Nav.Screen.PANTRY);
    }

    @FXML
    private void onNavRecipes() {
        nav(Nav.Screen.RECIPES);
    }

    @FXML
    private void onNavAdd() {
        nav(Nav.Screen.ADD);
    }

    @FXML
    private void onNavShopping() {
        nav(Nav.Screen.SHOPPING);
    }

    @FXML
    private void onNavProfile() {
        nav(Nav.Screen.PROFILE);
    }

    private void showNotImplementedMessage() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Coming Soon");
        alert.setHeaderText(null);
        alert.setContentText("This feature is not implemented in the current version.");
        alert.showAndWait();
    }

    private void nav(Nav.Screen screen) {
        try {
            Stage stage = (Stage) Stage.getWindows()
                    .stream()
                    .filter(window -> window.isShowing())
                    .findFirst()
                    .orElse(null);

            if (stage != null) {
                Nav.go(stage, screen);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}