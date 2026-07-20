package com.smartpantry.controllers;

import com.google.cloud.Timestamp;
import com.google.gson.JsonObject;
import com.smartpantry.model.Ingredient;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.GeminiRecipeService;
import com.smartpantry.services.OpenCvSupport;
import com.smartpantry.services.Session;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import java.io.ByteArrayInputStream;
import java.util.Base64;

public class CameraCaptureController {

  @FXML
  private ImageView previewImageView;

  @FXML
  private Label statusLabel;

  @FXML
  private Label resultLabel;

  @FXML
  private Button captureButton;

  @FXML
  private Button retakeButton;

  @FXML
  private Button useButton;

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

  private VideoCapture camera;
  private volatile boolean previewRunning = false;
  private volatile Mat latestFrame;
  private Mat capturedFrame;
  private Thread previewThread;

  @FXML
  public void initialize() {
    addButton.setDisable(true);
    retakeButton.setVisible(false);
    retakeButton.setManaged(false);
    useButton.setVisible(false);
    useButton.setManaged(false);

    setStatus("Opening camera...", true);
    openCameraAndStartPreview();
  }

  private void openCameraAndStartPreview() {
    Task<Boolean> openTask = new Task<>() {
      @Override
      protected Boolean call() {
        try {
          OpenCvSupport.ensureLoaded();
          camera = new VideoCapture(0);
          return camera.isOpened();
        } catch (Exception | Error e) {
          return false;
        }
      }
    };

    openTask.setOnSucceeded(event -> {
      if (Boolean.TRUE.equals(openTask.getValue())) {
        setStatus("Point the camera at an ingredient and tap Capture.", true);
        startPreviewLoop();
      } else {
        cameraUnavailable();
      }
    });

    openTask.setOnFailed(event -> cameraUnavailable());

    Thread thread = new Thread(openTask, "camera-open");
    thread.setDaemon(true);
    thread.start();
  }

  private void cameraUnavailable() {
    setStatus(
        "The camera could not be opened. On macOS, check that your "
            + "terminal/IDE has camera access under System Settings "
            + "\u2192 Privacy & Security \u2192 Camera, then try again "
            + "\u2014 or go back and choose \"Upload Photo\" instead.",
        false);
    captureButton.setDisable(true);
  }

  private void startPreviewLoop() {
    previewRunning = true;

    previewThread = new Thread(() -> {
      Mat frame = new Mat();

      while (previewRunning && camera != null && camera.isOpened()) {
        try {
          if (camera.read(frame) && !frame.empty()) {
            Mat frameCopy = frame.clone();
            latestFrame = frameCopy;

            Image fxImage = toFxImage(frameCopy);
            if (fxImage != null) {
              Platform.runLater(() -> previewImageView.setImage(fxImage));
            }
          }
          Thread.sleep(66);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          break;
        } catch (Exception ignored) {
        }
      }
    }, "camera-preview");

    previewThread.setDaemon(true);
    previewThread.start();
  }

  // encodes opencv image to base64 to be passed into gemini
  private Image toFxImage(Mat mat) {
    try {
      MatOfByte buffer = new MatOfByte();
      Imgcodecs.imencode(".jpg", mat, buffer);
      return new Image(new ByteArrayInputStream(buffer.toArray()));
    } catch (Exception e) {
      return null;
    }
  }

  @FXML
  private void handleCapture() {
    Mat frame = latestFrame;
    if (frame == null) {
      setStatus("No camera frame yet — wait a moment and try again.", false);
      return;
    }

    capturedFrame = frame;
    previewRunning = false;

    captureButton.setVisible(false);
    captureButton.setManaged(false);
    retakeButton.setVisible(true);
    retakeButton.setManaged(true);
    useButton.setVisible(true);
    useButton.setManaged(true);

    setStatus("Like the photo? Tap \"Use Photo\" to identify it, or retake.", true);
  }

  @FXML
  private void handleRetake() {
    capturedFrame = null;

    captureButton.setVisible(true);
    captureButton.setManaged(true);
    retakeButton.setVisible(false);
    retakeButton.setManaged(false);
    useButton.setVisible(false);
    useButton.setManaged(false);

    resultLabel.setText("");
    addButton.setDisable(true);

    setStatus("Point the camera at an ingredient and tap Capture.", true);
    startPreviewLoop();
  }

  @FXML
  private void handleUsePhoto() {
    if (capturedFrame == null) {
      setStatus("Nothing captured yet.", false);
      return;
    }

    useButton.setDisable(true);
    retakeButton.setDisable(true);
    resultLabel.setText("Identifying ingredient...");
    setStatus("Analyzing photo...", true);

    Task<JsonObject> task = new Task<>() {
      @Override
      protected JsonObject call() throws Exception {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", capturedFrame, buffer);
        String base64 = Base64.getEncoder().encodeToString(buffer.toArray());
        return geminiService.identifyIngredientFromImage(base64, "image/png");
      }
    };

    task.setOnSucceeded(event -> {
      useButton.setDisable(false);
      retakeButton.setDisable(false);
      fillFromGemini(task.getValue());
    });

    task.setOnFailed(event -> {
      useButton.setDisable(false);
      retakeButton.setDisable(false);
      resultLabel.setText("Could not identify the ingredient.");
      setStatus("Detection error: " + getErrorMessage(task.getException()), false);
    });

    Thread thread = new Thread(task, "camera-vision-detect");
    thread.setDaemon(true);
    thread.start();
  }

  private void fillFromGemini(JsonObject result) {
    Platform.runLater(() -> {
      if (result == null) {
        resultLabel.setText("Could not identify the ingredient.");
        setStatus("No detection result was returned.", false);
        addButton.setDisable(true);
        return;
      }

      String name = getString(result, "name");
      String category = getString(result, "category");
      String unit = getString(result, "unit");
      String quantity = "1.0";

      if (result.has("quantity") && !result.get("quantity").isJsonNull()) {
        try {
          quantity = String.valueOf(result.get("quantity").getAsDouble());
        } catch (Exception exception) {
          quantity = "1.0";
        }
      }

      detectedNameField.setText(name);
      detectedCategoryField.setText(category);
      detectedQuantityField.setText(quantity);
      detectedUnitField.setText(unit);

      if (name.isBlank()) {
        resultLabel.setText("Could not identify it. Enter the details manually.");
        setStatus("Review the fields and enter an ingredient name.", false);
        addButton.setDisable(true);
      } else {
        resultLabel.setText("Detected: " + name + ". Review and confirm below.");
        setStatus("Review the details before saving.", true);
        addButton.setDisable(false);
      }
    });
  }

  @FXML
  private void handleAddDetectedItem() {
    String name = getFieldText(detectedNameField);

    if (name.isBlank()) {
      setStatus("Enter an ingredient name first.", false);
      return;
    }

    if (!firebaseService.isConnected()) {
      setStatus("Not connected to Firebase.", false);
      return;
    }

    double quantity = 1.0;
    String quantityText = getFieldText(detectedQuantityField);

    if (!quantityText.isBlank()) {
      try {
        quantity = Double.parseDouble(quantityText);
      } catch (NumberFormatException exception) {
        setStatus("Quantity must be a number.", false);
        return;
      }
    }

    Ingredient ingredient = new Ingredient(
        name,
        quantity,
        getFieldText(detectedUnitField),
        getFieldText(detectedCategoryField));

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

    task.setOnSucceeded(event -> {
      setStatus("\"" + ingredient.getName() + "\" added to your pantry!", true);

      PauseTransition pause = new PauseTransition(Duration.seconds(1));
      pause.setOnFinished(finishedEvent -> closeCameraAndNav(Nav.Screen.PANTRY));
      pause.play();
    });

    task.setOnFailed(event -> {
      setStatus("Failed to add: " + getErrorMessage(task.getException()), false);
      addButton.setDisable(false);
    });

    Thread thread = new Thread(task, "camera-add-ingredient");
    thread.setDaemon(true);
    thread.start();
  }

  @FXML
  private void handleBack() {
    closeCameraAndNav(Nav.Screen.ADD);
  }

  @FXML
  private void onNavPantry() {
    closeCameraAndNav(Nav.Screen.PANTRY);
  }

  @FXML
  private void onNavRecipes() {
    closeCameraAndNav(Nav.Screen.RECIPES);
  }

  @FXML
  private void onNavAdd() {
    closeCameraAndNav(Nav.Screen.ADD);
  }

  @FXML
  private void onNavShopping() {
    closeCameraAndNav(Nav.Screen.SHOPPING);
  }

  @FXML
  private void onNavProfile() {
    closeCameraAndNav(Nav.Screen.PROFILE);
  }

  private void closeCameraAndNav(Nav.Screen screen) {
    previewRunning = false;

    if (previewThread != null) {
      previewThread.interrupt();
    }

    if (camera != null && camera.isOpened()) {
      try {
        camera.release();
      } catch (Exception ignored) {
      }
    }

    try {
      Stage stage = (Stage) previewImageView.getScene().getWindow();
      Nav.go(stage, screen);
    } catch (Exception exception) {
      setStatus("Navigation error: " + exception.getMessage(), false);
    }
  }

  private String getString(JsonObject obj, String key) {
    if (obj.has(key) && !obj.get(key).isJsonNull()) {
      return obj.get(key).getAsString();
    }
    return "";
  }

  private String getFieldText(TextField field) {
    String text = field.getText();
    return text == null ? "" : text.trim();
  }

  private String getErrorMessage(Throwable exception) {
    if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
      return "Unknown error";
    }
    return exception.getMessage();
  }

  private void setStatus(String message, boolean ok) {
    Platform.runLater(() -> {
      if (statusLabel == null) {
        return;
      }
      statusLabel.setText(message);
      statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    });
  }
}
