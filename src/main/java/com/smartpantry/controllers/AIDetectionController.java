package com.smartpantry.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AIDetectionController {

    @FXML
    private Label detectionResultLabel;

    private final List<String> detectedItems = new ArrayList<>();

    @FXML
    private void handleRunDetection() {
        // Temporary result until image recognition / Firebase storage is ready.
        detectedItems.clear();
        detectedItems.add("Eggs");
        detectedItems.add("Milk");
        detectedItems.add("Spinach");

        detectionResultLabel.setText("Detected sample items: eggs, milk, spinach");
    }

    @FXML
    private void handleAddDetectedItems() {
        if (detectedItems.isEmpty()) {
            detectionResultLabel.setText("Run detection before adding items.");
            return;
        }

        // Placeholder until this is connected to the pantry/Firebase service.
        detectionResultLabel.setText("Detected items are ready to add once pantry storage is connected.");
    }

    @FXML
    private void handleBackToPantry() {
        try {
            Nav.go((Stage) detectionResultLabel.getScene().getWindow(), Nav.Screen.PANTRY);
        } catch (IOException e) {
            detectionResultLabel.setText("Failed to load pantry screen: " + e.getMessage());
        }
    }
}