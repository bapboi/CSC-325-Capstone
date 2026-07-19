package com.smartpantry.controllers;

import com.google.cloud.Timestamp;
import com.google.gson.JsonObject;
import com.smartpantry.model.Ingredient;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.GeminiRecipeService;
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
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

public class AIDetectionController {

  @FXML
  private VBox photoPlaceholder;

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
  private ImageView ingredientImageView;

  @FXML
  private Label ingredientPhotoLabel;

  @FXML
  private Button addButton;

  private final FirebaseService firebaseService =
          FirebaseService.getInstance();

  private final GeminiRecipeService geminiService =
          new GeminiRecipeService();

  @FXML
  public void initialize() {
    addButton.setDisable(true);

    detectionResultLabel.setText(
            "Choose a photo to identify an ingredient."
    );

    resetPhotoPlaceholder();

    setStatus(
            "Upload a clear photo of one ingredient.",
            true
    );
  }

  @FXML
  private void handleUploadImage() {
    FileChooser chooser = new FileChooser();

    chooser.setTitle(
            "Choose a photo of the ingredient"
    );

    chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                    "Images",
                    "*.png",
                    "*.jpg",
                    "*.jpeg"
            )
    );

    File file = chooser.showOpenDialog(
            detectionResultLabel
                    .getScene()
                    .getWindow()
    );

    if (file == null) {
      return;
    }

    showSelectedImage(file);

    detectionResultLabel.setText(
            "Identifying ingredient..."
    );

    setStatus(
            "Analyzing " + file.getName() + "...",
            true
    );

    clearFields();

    Task<JsonObject> task = new Task<>() {
      @Override
      protected JsonObject call() throws Exception {
        byte[] bytes =
                Files.readAllBytes(file.toPath());

        String base64 =
                Base64.getEncoder()
                        .encodeToString(bytes);

        String fileName =
                file.getName().toLowerCase();

        String mime =
                fileName.endsWith(".png")
                        ? "image/png"
                        : "image/jpeg";

        return geminiService.identifyIngredientFromImage(
                base64,
                mime
        );
      }
    };

    task.setOnSucceeded(event ->
            fillFromGemini(task.getValue())
    );

    task.setOnFailed(event -> {
      detectionResultLabel.setText(
              "Could not identify the ingredient."
      );

      setStatus(
              "Detection error: "
                      + getErrorMessage(
                      task.getException()
              ),
              false
      );
    });

    Thread thread =
            new Thread(
                    task,
                    "ai-upload-detect"
            );

    thread.setDaemon(true);
    thread.start();
  }

  private void showSelectedImage(File file) {
    try {
      Image image = new Image(
              file.toURI().toString(),
              false
      );

      if (image.isError()) {
        throw new IllegalArgumentException(
                "Image could not be loaded"
        );
      }

      ingredientImageView.setImage(image);
      photoPlaceholder.setVisible(false);
      photoPlaceholder.setManaged(false);
    } catch (Exception exception) {
      resetPhotoPlaceholder();

      setStatus(
              "The photo preview could not be displayed.",
              false
      );
    }
  }

  private void resetPhotoPlaceholder() {
    ingredientImageView.setImage(null);

    photoPlaceholder.setVisible(true);
    photoPlaceholder.setManaged(true);

    ingredientPhotoLabel.setText(
            "No photo selected"
    );
  }

  private void fillFromGemini(JsonObject result) {
    Platform.runLater(() -> {
      if (result == null) {
        detectionResultLabel.setText(
                "Could not identify the ingredient."
        );

        setStatus(
                "No detection result was returned.",
                false
        );

        addButton.setDisable(true);
        return;
      }

      String name =
              getString(result, "name");

      String category =
              getString(result, "category");

      String unit =
              getString(result, "unit");

      String quantity = "1.0";

      if (result.has("quantity")
              && !result.get("quantity")
              .isJsonNull()) {

        try {
          quantity =
                  String.valueOf(
                          result.get("quantity")
                                  .getAsDouble()
                  );
        } catch (Exception exception) {
          quantity = "1.0";
        }
      }

      detectedNameField.setText(name);
      detectedCategoryField.setText(category);
      detectedQuantityField.setText(quantity);
      detectedUnitField.setText(unit);

      if (name.isBlank()) {
        detectionResultLabel.setText(
                "Could not identify it. Enter the details manually."
        );

        setStatus(
                "Review the fields and enter an ingredient name.",
                false
        );

        addButton.setDisable(true);
      } else {
        detectionResultLabel.setText(
                "Detected: "
                        + name
                        + ". Review and confirm below."
        );

        setStatus(
                "Review the details before saving.",
                true
        );

        addButton.setDisable(false);
      }
    });
  }

  @FXML
  private void handleAddDetectedItems() {
    String name =
            getFieldText(detectedNameField);

    if (name.isBlank()) {
      setStatus(
              "Enter an ingredient name first.",
              false
      );

      return;
    }

    if (!firebaseService.isConnected()) {
      setStatus(
              "Not connected to Firebase.",
              false
      );

      return;
    }

    double quantity = 1.0;

    String quantityText =
            getFieldText(
                    detectedQuantityField
            );

    if (!quantityText.isBlank()) {
      try {
        quantity =
                Double.parseDouble(quantityText);
      } catch (NumberFormatException exception) {
        setStatus(
                "Quantity must be a number.",
                false
        );

        return;
      }
    }

    Ingredient ingredient =
            new Ingredient(
                    name,
                    quantity,
                    getFieldText(
                            detectedUnitField
                    ),
                    getFieldText(
                            detectedCategoryField
                    )
            );

    ingredient.setDetectedByAI(true);

    ingredient.setUserID(
            Session.getInstance().getUid()
    );

    ingredient.setCreatedAt(
            Timestamp.now()
    );

    addButton.setDisable(true);

    setStatus(
            "Adding to pantry...",
            true
    );

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.addIngredient(
                ingredient
        );

        return null;
      }
    };

    task.setOnSucceeded(event -> {
      setStatus(
              "\""
                      + ingredient.getName()
                      + "\" added to your pantry!",
              true
      );

      PauseTransition pause =
              new PauseTransition(
                      Duration.seconds(1)
              );

      pause.setOnFinished(
              finishedEvent ->
                      nav(Nav.Screen.PANTRY)
      );

      pause.play();
    });

    task.setOnFailed(event -> {
      setStatus(
              "Failed to add: "
                      + getErrorMessage(
                      task.getException()
              ),
              false
      );

      addButton.setDisable(false);
    });

    Thread thread =
            new Thread(
                    task,
                    "ai-add-ingredient"
            );

    thread.setDaemon(true);
    thread.start();
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

  private String getString(
          JsonObject object,
          String key
  ) {
    if (object == null
            || !object.has(key)
            || object.get(key).isJsonNull()) {

      return "";
    }

    try {
      return object.get(key)
              .getAsString()
              .trim();
    } catch (Exception exception) {
      return "";
    }
  }

  private String getFieldText(
          TextField field
  ) {
    if (field == null
            || field.getText() == null) {

      return "";
    }

    return field.getText().trim();
  }

  private String getErrorMessage(
          Throwable exception
  ) {
    if (exception == null
            || exception.getMessage() == null
            || exception.getMessage().isBlank()) {

      return "Unknown error";
    }

    return exception.getMessage();
  }

  private void nav(Nav.Screen screen) {
    Platform.runLater(() -> {
      try {
        Stage stage =
                (Stage) detectionResultLabel
                        .getScene()
                        .getWindow();

        Nav.go(stage, screen);
      } catch (IOException exception) {
        setStatus(
                "Navigation error: "
                        + exception.getMessage(),
                false
        );
      }
    });
  }

  private void setStatus(
          String message,
          boolean ok
  ) {
    Platform.runLater(() -> {
      if (statusLabel == null) {
        return;
      }

      statusLabel.setText(message);

      statusLabel.setStyle(
              ok
                      ? "-fx-text-fill: #2e7d32;"
                      : "-fx-text-fill: #c62828;"
      );
    });
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
}
