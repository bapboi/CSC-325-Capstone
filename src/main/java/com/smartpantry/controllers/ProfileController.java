package com.smartpantry.controllers;

import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.Session;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class ProfileController {

  @FXML
  private Label headerEmailLabel;

  @FXML
  private Label accountEmailLabel;

  @FXML
  private Label ingredientCountLabel;

  @FXML
  private Label savedRecipeCountLabel;

  @FXML
  private Label versionLabel;

  private final FirebaseService firebaseService = FirebaseService.getInstance();

  @FXML
  private void onIngredientCountClicked() {
    nav(Nav.Screen.PANTRY);
  }

  @FXML
  private void onSavedRecipesClicked() {
    nav(Nav.Screen.SAVED_RECIPES);
  }

  @FXML
  public void initialize() {
    loadUserEmail();
    loadProfileCounts();

    versionLabel.setText("1.0.0");
  }

  private void loadUserEmail() {
    String email = Session.getInstance().getEmail();

    if (email == null || email.isEmpty()) {
      email = "Unknown";
    }

    headerEmailLabel.setText(email);
    accountEmailLabel.setText(email);
  }

  private void loadProfileCounts() {

    if (!firebaseService.isConnected()) {
      ingredientCountLabel.setText("0");
      savedRecipeCountLabel.setText("0");
      return;
    }

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {

        int ingredientCount = firebaseService.getAllIngredients().size();
        int savedRecipeCount = firebaseService.getSavedRecipes().size();

        Platform.runLater(() -> {
          ingredientCountLabel.setText(String.valueOf(ingredientCount));
          savedRecipeCountLabel.setText(String.valueOf(savedRecipeCount));
        });

        return null;
      }
    };

    task.setOnFailed(event -> {
      ingredientCountLabel.setText("0");
      savedRecipeCountLabel.setText("0");
      task.getException().printStackTrace();
    });

    Thread thread = new Thread(task, "load-profile-counts");
    thread.setDaemon(true);
    thread.start();
  }

  @FXML
  private void onSignOut() {
    Session.getInstance().clear();
    nav(Nav.Screen.LOGIN);
  }

  @FXML
  private void onNavSettings() {
    nav(Nav.Screen.SETTINGS);
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
    // Already on the Profile screen.
  }

  private void nav(Nav.Screen screen) {
    try {
      Nav.go((Stage) headerEmailLabel.getScene().getWindow(), screen);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}