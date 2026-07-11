package com.smartpantry.controllers;

import com.google.cloud.Timestamp;
import com.smartpantry.model.Ingredient;
import com.smartpantry.services.FirebaseService;
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
import java.time.LocalDate;
import java.time.ZoneId;
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
  private final FirebaseService firebaseService = FirebaseService.getInstance();
  private final ObservableList<String> pantryDisplayItems = FXCollections.observableArrayList();

  @FXML
  public void initialize() {
    pantryListView.setItems(pantryDisplayItems);

    String savedPath = firebaseService.resolveSavedCredentialPath();
    if (savedPath != null) {
      connectToFirebase(savedPath);
    } else {
      setStatus("Not connected - click \"Connect Firebase\"", false);
    }
  }

  @FXML
  private void onConnectFirebase() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Select your Firebase service account JSON key");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
    File file = chooser.showOpenDialog(connectButton.getScene().getWindow());
    if (file != null) {
      connectToFirebase(file.getAbsolutePath());
    }
  }

  private void connectToFirebase(String credentialPath) {
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.initialize(credentialPath);
        firebaseService.saveCredentialPath(credentialPath);
        return null;
      }
    };
    task.setOnSucceeded(e -> {
      setStatus("Connected to Firebase", true);
      onRefreshPantry();
    });
    task.setOnFailed(e -> setStatus("Connection failed: " + task.getException().getMessage(), false));
    new Thread(task, "firebase-connect").start();
  }

  @FXML
  private void onRefreshPantry() {
    if (!firebaseService.isConnected()) {
      setStatus("Connect Firebase to load pantry items", false);
      return;
    }
    Task<List<Ingredient>> task = new Task<>() {
      @Override
      protected List<Ingredient> call() throws Exception {
        return firebaseService.getAllIngredients();
      }
    };
    task.setOnSucceeded(e -> {
      pantryDisplayItems.clear();
      for (Ingredient ingredient : task.getValue()) {
        pantryDisplayItems.add(formatForDisplay(ingredient));
      }
    });
    task.setOnFailed(e -> setStatus("Failed to load pantry: " + task.getException().getMessage(), false));
    new Thread(task, "firebase-refresh").start();
  }

  private String formatForDisplay(Ingredient ingredient) {
    String category = (ingredient.getCategory() == null || ingredient.getCategory().isBlank())
        ? "Uncategorized"
        : ingredient.getCategory();
    return String.format("%s - %s %s (%s)",
        ingredient.getName(), ingredient.getQuantity(), ingredient.getUnit(), category);
  }

  // wip
  @FXML
  private void onChoosePhoto() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose a photo of the ingredient");
    chooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
    File file = chooser.showOpenDialog(photoStatusTitle.getScene().getWindow());
    if (file != null) {
      selectedPhotoFile = file;
      photoStatusTitle.setText("Photo selected");
      photoStatusSubtitle.setText(file.getName());
    }
  }

  @FXML
  private void onSave() {
    if (!firebaseService.isConnected()) {
      setStatus("Connect Firebase before saving", false);
      return;
    }

    String name = nameField.getText();
    if (name == null || name.isBlank()) {
      setStatus("Enter an ingredient name first", false);
      return;
    }

    double quantity;
    try {
      quantity = quantityField.getText() == null || quantityField.getText().isBlank()
          ? 1.0
          : Double.parseDouble(quantityField.getText());
    } catch (NumberFormatException ex) {
      setStatus("Quantity must be a number", false);
      return;
    }

    Ingredient ingredient = new Ingredient(name.trim(), quantity, unitField.getText(), categoryField.getText());

    ingredient.setUserID(Session.getInstance().getUid());

    LocalDate expiration = expirationDatePicker.getValue();
    if (expiration != null) {
      Date date = Date.from(expiration.atStartOfDay(ZoneId.systemDefault()).toInstant());
      ingredient.setExpirationDate(Timestamp.of(date));
    }

    if (selectedPhotoFile != null) {
      ingredient.setPhotoFileName(selectedPhotoFile.getName());
    }

    setStatus("Saving...", true);
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.addIngredient(ingredient);
        return null;
      }
    };
    task.setOnSucceeded(e -> {
      setStatus("Added " + ingredient.getName() + " to your pantry", true);
      clearForm();
      onRefreshPantry();
    });
    task.setOnFailed(e -> setStatus("Failed to save: " + task.getException().getMessage(), false));
    new Thread(task, "firebase-add-ingredient").start();
  }

  // ── Nav ──────────────────────────────────────────────────────────────────
  @FXML
  private void onNavPantry() {
    nav(Nav.Screen.PANTRY);
  }

  @FXML
  private void onNavAdd() {
    /* already here */ }

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

  private void nav(Nav.Screen screen) {
    try {
      Nav.go((Stage) connectButton.getScene().getWindow(), screen);
    } catch (Exception ex) {
      setStatus("Navigation error: " + ex.getMessage(), false);
    }
  }

  private void clearForm() {
    nameField.clear();
    quantityField.clear();
    unitField.clear();
    categoryField.clear();
    expirationDatePicker.setValue(null);
    selectedPhotoFile = null;
    photoStatusTitle.setText("Take Photo");
    photoStatusSubtitle.setText("Tap to open camera");
  }

  private void setStatus(String message, boolean ok) {
    Platform.runLater(() -> {
      statusLabel.setText(message);
      statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    });
  }
}
