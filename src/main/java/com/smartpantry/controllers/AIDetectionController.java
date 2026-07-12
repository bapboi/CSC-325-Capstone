package com.smartpantry.controllers;

import com.google.cloud.Timestamp;
import com.google.gson.JsonObject;
import com.smartpantry.model.Ingredient;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.GeminiRecipeService;
import com.smartpantry.services.Session;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class AIDetectionController {

    @FXML private Label detectionResultLabel;
    @FXML private Label statusLabel;

    // Editable fields so the user can review/correct before saving
    @FXML private TextField detectedNameField;
    @FXML private TextField detectedCategoryField;
    @FXML private TextField detectedQuantityField;
    @FXML private TextField detectedUnitField;

    @FXML private Button addButton;

    private final FirebaseService firebaseService = FirebaseService.getInstance();
    private final GeminiRecipeService geminiService = new GeminiRecipeService();

    @FXML
    public void initialize() {
        addButton.setDisable(true);
    }

    /** Opens the system webcam, captures a frame, sends it to Gemini. */
    @FXML
    private void handleTakePhoto() {
        detectionResultLabel.setText("Opening camera...");
        clearFields();

        Task<JsonObject> task = new Task<>() {
            @Override
            protected JsonObject call() throws Exception {
                com.github.sarxos.webcam.Webcam webcam = com.github.sarxos.webcam.Webcam.getDefault();
                if (webcam == null) throw new Exception("No camera found on this device.");
                webcam.open();
                try {
                    java.awt.image.BufferedImage image = webcam.getImage();
                    if (image == null) throw new Exception("Failed to capture image from camera.");
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(image, "jpg", baos);
                    String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
                    return geminiService.identifyIngredientFromImage(base64, "image/jpeg");
                } finally {
                    webcam.close();
                }
            }
        };

        task.setOnSucceeded(e -> fillFromGemini(task.getValue()));
        task.setOnFailed(e -> setStatus("Camera error: " + task.getException().getMessage(), false));
        new Thread(task, "ai-camera-capture").start();
    }

    /** Opens a file picker, sends the chosen image to Gemini. */
    @FXML
    private void handleUploadImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a photo of the ingredient");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(detectionResultLabel.getScene().getWindow());
        if (file == null) return;

        detectionResultLabel.setText("Identifying ingredient...");
        clearFields();

        Task<JsonObject> task = new Task<>() {
            @Override
            protected JsonObject call() throws Exception {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                String mime = file.getName().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
                return geminiService.identifyIngredientFromImage(base64, mime);
            }
        };

        task.setOnSucceeded(e -> fillFromGemini(task.getValue()));
        task.setOnFailed(e -> setStatus("Detection error: " + task.getException().getMessage(), false));
        new Thread(task, "ai-upload-detect").start();
    }

    /** Populates the editable fields with what Gemini returned. */
    private void fillFromGemini(JsonObject result) {
        Platform.runLater(() -> {
            String name = result.has("name") ? result.get("name").getAsString() : "";
            String category = result.has("category") ? result.get("category").getAsString() : "";
            String quantity = result.has("quantity") ? String.valueOf(result.get("quantity").getAsDouble()) : "1.0";
            String unit = result.has("unit") ? result.get("unit").getAsString() : "";

            detectedNameField.setText(name);
            detectedCategoryField.setText(category);
            detectedQuantityField.setText(quantity);
            detectedUnitField.setText(unit);

            detectionResultLabel.setText("Detected: " + name + " — review and confirm before adding.");
            addButton.setDisable(name.isBlank());
        });
    }

    /** Writes the detected (and optionally corrected) ingredient to the pantry. */
    @FXML
    private void handleAddDetectedItems() {
        String name = detectedNameField.getText().trim();
        if (name.isBlank()) {
            setStatus("No ingredient detected yet.", false);
            return;
        }
        if (!firebaseService.isConnected()) {
            setStatus("Not connected to Firebase.", false);
            return;
        }

        double qty;
        try {
            qty = Double.parseDouble(detectedQuantityField.getText().trim());
        } catch (NumberFormatException e) {
            qty = 1.0;
        }

        Ingredient ingredient = new Ingredient(name, qty,
                detectedUnitField.getText().trim(),
                detectedCategoryField.getText().trim());
        ingredient.setDetectedByAI(true);
        ingredient.setUserID(Session.getInstance().getUid());
        ingredient.setCreatedAt(Timestamp.now());

        addButton.setDisable(true);
        setStatus("Adding to pantry...", true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                firebaseService.addIngredient(ingredient);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setStatus("\"" + name + "\" added to your pantry.", true);
            clearFields();
            addButton.setDisable(true);
        });
        task.setOnFailed(e -> {
            setStatus("Failed to add: " + task.getException().getMessage(), false);
            addButton.setDisable(false);
        });
        new Thread(task, "ai-add-ingredient").start();
    }

    @FXML
    private void handleBackToPantry() {
        try {
            Nav.go((Stage) detectionResultLabel.getScene().getWindow(), Nav.Screen.PANTRY);
        } catch (IOException e) {
            setStatus("Navigation error: " + e.getMessage(), false);
        }
    }

    private void clearFields() {
        Platform.runLater(() -> {
            detectedNameField.clear();
            detectedCategoryField.clear();
            detectedQuantityField.clear();
            detectedUnitField.clear();
            addButton.setDisable(true);
        });
    }

    private void setStatus(String msg, boolean ok) {
        Platform.runLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
        });
    }
}
