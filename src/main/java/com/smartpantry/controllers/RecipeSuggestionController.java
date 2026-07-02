package com.smartpantry.controllers;

import com.smartpantry.model.Ingredient;
import com.smartpantry.model.ShoppingItem;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.GeminiRecipeService;
import com.smartpantry.services.Session;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeSuggestionController {

  @FXML
  private Button findButton;
  @FXML
  private Button addMissingButton;
  @FXML
  private ProgressIndicator progressIndicator;
  @FXML
  private Label statusLabel;
  @FXML
  private Label resultLabel;
  @FXML
  private Hyperlink resultLink;

  private final FirebaseService firebaseService = FirebaseService.getInstance();
  private final GeminiRecipeService geminiService = new GeminiRecipeService();

  // Held so the "add missing to shopping list" button can use it
  private List<String> lastMissingIngredients = List.of();

  @FXML
  public void initialize() {
    progressIndicator.setVisible(false);
    resultLink.setVisible(false);
    addMissingButton.setVisible(false);
  }

  @FXML
  private void onFindRecipe() {
    if (!firebaseService.isConnected()) {
      setStatus("Not connected to Firebase", false);
      return;
    }

    findButton.setDisable(true);
    addMissingButton.setVisible(false);
    progressIndicator.setVisible(true);
    resultLink.setVisible(false);
    resultLabel.setText("");
    setStatus("Reading your pantry...", true);

    Task<GeminiRecipeService.RecipeResult> task = new Task<>() {
      @Override
      protected GeminiRecipeService.RecipeResult call() throws Exception {
        List<Ingredient> pantry = firebaseService.getAllIngredients();
        if (pantry.isEmpty())
          throw new IllegalStateException("Your pantry is empty — add some ingredients first.");
        List<String> names = pantry.stream().map(Ingredient::getName).collect(Collectors.toList());
        return geminiService.findRecipe(names);
      }
    };

    task.setOnSucceeded(e -> {
      GeminiRecipeService.RecipeResult result = task.getValue();
      resultLabel.setText(result.description());

      if (result.link() != null) {
        resultLink.setText(result.link());
        resultLink.setVisible(true);
      }

      // Ask Gemini what ingredients the recipe needs that aren't already in the
      // pantry
      if (result.description() != null && !result.description().isBlank()) {
        fetchMissingIngredients(result.description());
      }

      setStatus("Done", true);
      progressIndicator.setVisible(false);
      findButton.setDisable(false);
    });

    task.setOnFailed(e -> {
      setStatus(task.getException().getMessage(), false);
      progressIndicator.setVisible(false);
      findButton.setDisable(false);
    });

    new Thread(task, "gemini-recipe-search").start();
  }

  /**
   * Asks Gemini which ingredients from the suggested recipe are NOT already in
   * the pantry.
   */
  private void fetchMissingIngredients(String recipeDescription) {
    Task<List<String>> task = new Task<>() {
      @Override
      protected List<String> call() throws Exception {
        List<Ingredient> pantry = firebaseService.getAllIngredients();
        List<String> pantryNames = pantry.stream().map(Ingredient::getName).collect(Collectors.toList());
        return geminiService.findMissingIngredients(recipeDescription, pantryNames);
      }
    };
    task.setOnSucceeded(e -> {
      lastMissingIngredients = task.getValue();
      if (!lastMissingIngredients.isEmpty()) {
        addMissingButton.setText("Add " + lastMissingIngredients.size() + " missing to Shopping List");
        addMissingButton.setVisible(true);
      }
    });
    task.setOnFailed(e -> {
      /* non-critical — don't surface this to the user */ });
    new Thread(task, "gemini-missing-ingredients").start();
  }

  @FXML
  private void onAddMissingToShoppingList() {
    if (lastMissingIngredients.isEmpty() || !firebaseService.isConnected())
      return;
    String uid = Session.getInstance().getUid();

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        for (String name : lastMissingIngredients) {
          firebaseService.addShoppingItem(new ShoppingItem(name, 1.0, "", uid));
        }
        return null;
      }
    };
    task.setOnSucceeded(e -> {
      addMissingButton.setVisible(false);
      setStatus("Added " + lastMissingIngredients.size() + " item(s) to your shopping list", true);
      lastMissingIngredients = List.of();
    });
    task.setOnFailed(e -> setStatus("Failed to add items: " + task.getException().getMessage(), false));
    new Thread(task, "add-missing-shopping").start();
  }

  @FXML
  private void onOpenLink() {
    try {
      if (Desktop.isDesktopSupported())
        Desktop.getDesktop().browse(new URI(resultLink.getText()));
    } catch (Exception ex) {
      setStatus("Could not open link: " + ex.getMessage(), false);
    }
  }

  // ── Nav ──────────────────────────────────────────────────────────────────
  @FXML
  private void onNavPantry() {
    nav(Nav.Screen.PANTRY);
  }

  @FXML
  private void onNavAdd() {
    nav(Nav.Screen.ADD);
  }

  @FXML
  private void onNavRecipes() {
    /* already here */ }

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
      Nav.go((Stage) findButton.getScene().getWindow(), screen);
    } catch (Exception ex) {
      setStatus("Navigation error: " + ex.getMessage(), false);
    }
  }

  private void setStatus(String message, boolean ok) {
    Platform.runLater(() -> {
      statusLabel.setText(message);
      statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    });
  }
}
