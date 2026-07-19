package com.smartpantry.controllers;

import com.google.cloud.Timestamp;
import com.google.gson.JsonObject;
import com.smartpantry.model.Ingredient;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.GeminiRecipeService;
import com.smartpantry.services.Session;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public class AddIngredientController {

  @FXML
  private Label statusLabel;

  @FXML
  private Button connectButton;

  @FXML
  private Label photoStatusTitle;

  @FXML
  private Label photoStatusSubtitle;

  @FXML
  private TextField nameField;

  @FXML
  private TextField quantityField;

  @FXML
  private TextField unitField;

  @FXML
  private TextField categoryField;

  @FXML
  private DatePicker expirationDatePicker;

  @FXML
  private ListView<String> pantryListView;

  private File selectedPhotoFile;

  private final FirebaseService firebaseService =
          FirebaseService.getInstance();

  private final GeminiRecipeService geminiService =
          new GeminiRecipeService();

  private final ObservableList<String> pantryDisplayItems =
          FXCollections.observableArrayList();

  @FXML
  public void initialize() {
    pantryListView.setItems(pantryDisplayItems);

    String savedPath =
            firebaseService.resolveSavedCredentialPath();

    if (savedPath != null) {
      connectToFirebase(savedPath);
    } else {
      setStatus(
              "Not connected to Firebase",
              false
      );
    }
  }

  @FXML
  private void onConnectFirebase() {
    FileChooser chooser = new FileChooser();

    chooser.setTitle(
            "Select your Firebase service account JSON key"
    );

    chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                    "JSON files",
                    "*.json"
            )
    );

    File file = chooser.showOpenDialog(
            connectButton.getScene().getWindow()
    );

    if (file != null) {
      connectToFirebase(
              file.getAbsolutePath()
      );
    }
  }

  private void connectToFirebase(
          String credentialPath
  ) {
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.initialize(
                credentialPath
        );

        firebaseService.saveCredentialPath(
                credentialPath
        );

        return null;
      }
    };

    task.setOnSucceeded(event -> {
      setStatus(
              "Connected to Firebase",
              true
      );

      loadPantry(false);
    });

    task.setOnFailed(event -> {
      setStatus(
              "Failed to connect to Firebase: "
                      + getErrorMessage(
                      task.getException()
              ),
              false
      );
    });

    Thread thread =
            new Thread(task, "firebase-connect");

    thread.setDaemon(true);
    thread.start();
  }

  @FXML
  private void onTakePhoto() {
    nav(Nav.Screen.AI_DETECTION);
  }

  @FXML
  private void onChoosePhoto() {
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
            photoStatusTitle
                    .getScene()
                    .getWindow()
    );

    if (file == null) {
      return;
    }

    selectedPhotoFile = file;

    photoStatusTitle.setText(
            "Identifying ingredient..."
    );

    photoStatusSubtitle.setText(
            file.getName()
    );

    Task<JsonObject> task = new Task<>() {
      @Override
      protected JsonObject call()
              throws Exception {

        byte[] bytes =
                Files.readAllBytes(
                        file.toPath()
                );

        String base64 =
                Base64.getEncoder()
                        .encodeToString(bytes);

        String fileName =
                file.getName().toLowerCase();

        String mime =
                fileName.endsWith(".png")
                        ? "image/png"
                        : "image/jpeg";

        return geminiService
                .identifyIngredientFromImage(
                        base64,
                        mime
                );
      }
    };

    task.setOnSucceeded(event ->
            fillFromGemini(
                    task.getValue(),
                    file.getName()
            )
    );

    task.setOnFailed(event -> {
      photoStatusTitle.setText(
              "Photo selected"
      );

      photoStatusSubtitle.setText(
              file.getName()
      );

      setStatus(
              "Could not identify ingredient: "
                      + getErrorMessage(
                      task.getException()
              ),
              false
      );
    });

    Thread thread =
            new Thread(
                    task,
                    "gemini-vision-upload"
            );

    thread.setDaemon(true);
    thread.start();
  }

  private void fillFromGemini(
          JsonObject result,
          String sourceLabel
  ) {
    Platform.runLater(() -> {
      if (result == null) {
        setStatus(
                "Could not identify the ingredient.",
                false
        );

        return;
      }

      if (result.has("name")
              && !result.get("name")
              .isJsonNull()) {

        String name =
                result.get("name")
                        .getAsString();

        if (!name.isBlank()) {
          nameField.setText(name);
        }
      }

      if (result.has("category")
              && !result.get("category")
              .isJsonNull()) {

        String category =
                result.get("category")
                        .getAsString();

        if (!category.isBlank()) {
          categoryField.setText(category);
        }
      }

      if (result.has("quantity")
              && !result.get("quantity")
              .isJsonNull()) {

        try {
          double quantity =
                  result.get("quantity")
                          .getAsDouble();

          quantityField.setText(
                  String.valueOf(quantity)
          );
        } catch (Exception exception) {
          quantityField.setText("1.0");
        }
      }

      if (result.has("unit")
              && !result.get("unit")
              .isJsonNull()) {

        String unit =
                result.get("unit")
                        .getAsString();

        if (!unit.isBlank()) {
          unitField.setText(unit);
        }
      }

      photoStatusTitle.setText(
              "Photo selected"
      );

      photoStatusSubtitle.setText(
              sourceLabel
      );

      setStatus(
              "Review the ingredient before saving.",
              true
      );
    });
  }

  @FXML
  private void onRefreshPantry() {
    loadPantry(true);
  }

  private void loadPantry(
          boolean showRefreshMessage
  ) {
    if (!firebaseService.isConnected()) {
      setStatus(
              "Not connected to Firebase yet",
              false
      );

      return;
    }

    Task<List<Ingredient>> task =
            new Task<>() {
              @Override
              protected List<Ingredient> call()
                      throws Exception {

                return firebaseService
                        .getAllIngredients();
              }
            };

    task.setOnSucceeded(event -> {
      pantryDisplayItems.clear();

      List<Ingredient> ingredients =
              task.getValue();

      if (ingredients != null) {
        for (Ingredient ingredient :
                ingredients) {

          String itemText =
                  ingredient.getName()
                          + " — "
                          + ingredient.getQuantity();

          if (ingredient.getUnit() != null
                  && !ingredient.getUnit()
                  .isBlank()) {

            itemText +=
                    " " + ingredient.getUnit();
          }

          pantryDisplayItems.add(itemText);
        }
      }

      if (showRefreshMessage) {
        setStatus(
                "Pantry refreshed",
                true
        );
      }
    });

    task.setOnFailed(event ->
            setStatus(
                    "Failed to load pantry: "
                            + getErrorMessage(
                            task.getException()
                    ),
                    false
            )
    );

    Thread thread =
            new Thread(task, "firebase-refresh");

    thread.setDaemon(true);
    thread.start();
  }

  @FXML
  private void onSave() {
    if (!firebaseService.isConnected()) {
      setStatus(
              "Connect Firebase before saving",
              false
      );

      return;
    }

    String name = nameField.getText();

    if (name == null || name.isBlank()) {
      setStatus(
              "Enter an ingredient name first",
              false
      );

      return;
    }

    double quantity;

    try {
      String quantityText =
              quantityField.getText();

      if (quantityText == null
              || quantityText.isBlank()) {

        quantity = 1.0;
      } else {
        quantity =
                Double.parseDouble(
                        quantityText.trim()
                );
      }
    } catch (NumberFormatException exception) {
      setStatus(
              "Quantity must be a number",
              false
      );

      return;
    }

    String unit = unitField.getText();
    String category = categoryField.getText();

    if (unit == null) {
      unit = "";
    }

    if (category == null) {
      category = "";
    }

    Ingredient ingredient =
            new Ingredient(
                    name.trim(),
                    quantity,
                    unit.trim(),
                    category.trim()
            );

    ingredient.setDetectedByAI(
            selectedPhotoFile != null
    );

    ingredient.setUserID(
            Session.getInstance().getUid()
    );

    ingredient.setCreatedAt(
            Timestamp.now()
    );

    LocalDate expirationDate =
            expirationDatePicker.getValue();

    if (expirationDate != null) {
      Date date = Date.from(
              expirationDate
                      .atStartOfDay(
                              ZoneId.systemDefault()
                      )
                      .toInstant()
      );

      ingredient.setExpirationDate(
              Timestamp.of(date)
      );
    }

    if (selectedPhotoFile != null) {
      ingredient.setPhotoFileName(
              selectedPhotoFile.getName()
      );
    }

    setStatus(
            "Saving...",
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
              "Added \""
                      + ingredient.getName()
                      + "\" to your pantry",
              true
      );

      clearForm();
      loadPantry(false);
    });

    task.setOnFailed(event ->
            setStatus(
                    "Failed to save: "
                            + getErrorMessage(
                            task.getException()
                    ),
                    false
            )
    );

    Thread thread =
            new Thread(
                    task,
                    "firebase-add-ingredient"
            );

    thread.setDaemon(true);
    thread.start();
  }

  private void clearForm() {
    nameField.clear();
    quantityField.clear();
    unitField.clear();
    categoryField.clear();

    expirationDatePicker.setValue(null);
    selectedPhotoFile = null;

    photoStatusTitle.setText(
            "Take Photo"
    );

    photoStatusSubtitle.setText(
            "Tap to open camera"
    );
  }

  @FXML
  private void onNavPantry() {
    nav(Nav.Screen.PANTRY);
  }

  @FXML
  private void onNavAdd() {
  }

  @FXML
  private void onNavRecipes() {
    nav(Nav.Screen.RECIPES);
  }

  @FXML
  private void onNavShopping() {
    nav(Nav.Screen.SHOPPING);
  }

  @FXML
  private void onNavProfile() {
    nav(Nav.Screen.PROFILE);
  }

  private void nav(
          Nav.Screen screen
  ) {
    try {
      Stage stage =
              (Stage) photoStatusTitle
                      .getScene()
                      .getWindow();

      Nav.go(stage, screen);
    } catch (Exception exception) {
      setStatus(
              "Navigation error: "
                      + exception.getMessage(),
              false
      );
    }
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
}
