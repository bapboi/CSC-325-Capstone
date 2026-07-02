package com.smartpantry.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;

public final class Navigation {

    private Navigation() {
    }

    public static void goTo(ActionEvent event, String fxmlFileName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    Navigation.class.getResource("/com/smartpantry/fxml/" + fxmlFileName)
            );

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 700));
            stage.show();
        } catch (IOException exception) {
            showError("Navigation Error", "Could not open " + fxmlFileName + ".", exception.getMessage());
        }
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showError(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
