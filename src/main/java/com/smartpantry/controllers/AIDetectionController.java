package com.smartpantry.controllers;

import com.smartpantry.data.AppData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class AIDetectionController {

    @FXML
    private Label detectionResultLabel;

    @FXML
    private void handleRunDetection() {
        // Temporary result until image recognition / Firebase storage is ready.
        detectionResultLabel.setText("Detected sample items: eggs, milk, spinach");
    }

    @FXML
    private void handleAddDetectedItems() {
        addIfMissing("Eggs - detected item");
        addIfMissing("Milk - detected item");
        addIfMissing("Spinach - detected item");
        detectionResultLabel.setText("Detected items added to pantry.");
    }

    @FXML
    private void handleBackToPantry(ActionEvent event) {
        Navigation.goTo(event, "Pantry.fxml");
    }

    private void addIfMissing(String item) {
        if (!AppData.PANTRY_ITEMS.contains(item)) {
            AppData.PANTRY_ITEMS.add(item);
        }
    }
}
