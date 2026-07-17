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

  @FXML
  private Label detectionResultLabel;
  @FXML
  private Label statusLabel;
  @FXML
  private TextField detectedNameField;
  @FXML
  private TextField detectedCategoryField;
  @FXML
  private TextField detectedQuantityField;
  @FXML
  private TextField detectedUnitField;
  @FXML
  private Button addButton;

  private final FirebaseService firebaseService = FirebaseService.getInstance();
  private final GeminiRecipeService geminiService = new GeminiRecipeService();

  @FXML
  public void initialize() {
    addButton.setDisable(true);
    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
        javafx.util.Duration.millis(300));
    pause.setOnFinished(e -> handleTakePhoto());
    pause.play();
  }

  @FXML
  private void handleTakePhoto() {
    detectionResultLabel.setText("Opening camera...");
    clearFields();

    Task<JsonObject> task = new Task<>() {
      @Override
      protected JsonObject call() throws Exception {
        com.github.sarxos.webcam.Webcam webcam = com.github.sarxos.webcam.Webcam.getDefault();
        if (webcam == null)
          throw new Exception("No camera found on this device.");
        webcam.open();
        try {
          java.awt.image.BufferedImage image = webcam.getImage();
          if (image == null)
            throw new Exception("Failed to capture image from camera.");
          java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
          javax.imageio.ImageIO.write(image, "jpg", baos);
          String base64 = java.util.Base64.getEncoder()
              .encodeToString(baos.toByteArray());
          return geminiService.identifyIngredientFromImage(base64, "image/jpeg");
        } finally {
          webcam.close();
        }
      }
    };

    task.setOnSucceeded(e -> fillFromGemini(task.getValue()));
    task.setOnFailed(e -> {
      detectionResultLabel.setText("Camera unavailable.");
      setStatus("Camera error: " + task.getException().getMessage()
          + " — try uploading an image instead.", false);
    });
    new Thread(task, "ai-camera-capture").start();
  }

  @FXML
  private void handleUploadImage() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose a photo of the ingredient");
    chooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
    File file = chooser.showOpenDialog(detectionResultLabel.getScene().getWindow());
    if (file == null)
      return;

    detectionResultLabel.setText("Identifying ingredient...");
    clearFields();

    Task<JsonObject> task = new Task<>() {
      @Override
      protected JsonObject call() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
        String mime = file.getName().toLowerCase().endsWith(".png")
            ? "image/png"
            : "image/jpeg";
        return geminiService.identifyIngredientFromImage(base64, mime);
      }
    };

    task.setOnSucceeded(e -> fillFromGemini(task.getValue()));
    task.setOnFailed(e -> setStatus(
        "Detection error: " + task.getException().getMessage(), false));
    new Thread(task, "ai-upload-detect").start();
  }

  private void fillFromGemini(JsonObject result) {
    Platform.runLater(() -> {
      String name = s(result, "name");
      String cat = s(result, "category");
      String qty = result.has("quantity")
          ? String.valueOf(result.get("quantity").getAsDouble())
          : "1.0";
      String unit = s(result, "unit");

      detectedNameField.setText(name);
      detectedCategoryField.setText(cat);
      detectedQuantityField.setText(qty);
      detectedUnitField.setText(unit);

      detectionResultLabel.setText(name.isBlank()
          ? "Could not identify — please edit manually."
          : "Detected: " + name + " — review and confirm below.");
      addButton.setDisable(name.isBlank());
    });
  }

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

    Ingredient ingredient = new Ingredient(
        name, qty,
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
      setStatus("\"" + name + "\" added to your pantry!", true);
      javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1));
      pause.setOnFinished(ev -> nav(Nav.Screen.PANTRY));
      pause.play();
    });
    task.setOnFailed(e -> {
      setStatus("Failed to add: " + task.getException().getMessage(), false);
      addButton.setDisable(false);
    });
    new Thread(task, "ai-add-ingredient").start();
  }

  @FXML
  private void handleBackToPantry() {
    nav(Nav.Screen.ADD);
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

  private String s(JsonObject obj, String key) {
    return (obj.has(key) && !obj.get(key).isJsonNull())
        ? obj.get(key).getAsString()
        : "";
  }

  private void nav(Nav.Screen screen) {
    Platform.runLater(() -> {
      try {
        Nav.go((Stage) detectionResultLabel.getScene().getWindow(), screen);
      } catch (IOException e) {
        setStatus("Navigation error: " + e.getMessage(), false);
      }
    });
  }

  private void setStatus(String msg, boolean ok) {
    Platform.runLater(() -> {
      statusLabel.setText(msg);
      statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    });
  }
}
