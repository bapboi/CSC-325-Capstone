package com.smartpantry.controllers;

import com.smartpantry.model.Recipe;
import com.smartpantry.model.ShoppingItem;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.OgImageFetcher;
import com.smartpantry.services.SelectedRecipeStore;
import com.smartpantry.services.Session;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public class RecipeDetailsController {

  @FXML
  private Button heartButton;
  @FXML
  private ImageView recipeImageView;
  @FXML
  private Label imagePlaceholderLabel;

  @FXML
  private Label ingredientsTabLabel;
  @FXML
  private Label instructionsTabLabel;
  @FXML
  private VBox ingredientsContent;
  @FXML
  private VBox instructionsContent;

  @FXML
  private Label recipeNameLabel;
  @FXML
  private Label cookTimeLabel;
  @FXML
  private Label difficultyLabel;
  @FXML
  private Label servingsLabel;
  @FXML
  private Label pantryMatchLabel;
  @FXML
  private ProgressBar pantryMatchProgressBar;

  @FXML
  private ListView<String> availableIngredientsListView;
  @FXML
  private ListView<String> missingIngredientsListView;
  @FXML
  private TextArea instructionsTextArea;

  @FXML
  private Button addMissingButton;
  @FXML
  private Button addAllMissingButton;
  @FXML
  private Label statusLabel;

  private final FirebaseService firebaseService = FirebaseService.getInstance();
  private Recipe recipe;

  @FXML
  private void initialize() {
    recipe = SelectedRecipeStore.getSelectedRecipe();

    if (recipe == null) {
      statusLabel.setText("No recipe selected. Go back and choose one.");
      addMissingButton.setDisable(true);
      if (addAllMissingButton != null)
        addAllMissingButton.setDisable(true);
      if (heartButton != null)
        heartButton.setDisable(true);
      return;
    }

    displayRecipe(recipe);
    loadRecipeImage(recipe);

    addMissingButton.setDisable(true);
    updateAddAllMissingButtonState();
    missingIngredientsListView.getSelectionModel().selectedItemProperty()
        .addListener((obs, old, selected) -> addMissingButton.setDisable(selected == null));

    showIngredientsTab();
  }

  private void displayRecipe(Recipe r) {
    recipeNameLabel.setText(or(r.getName(), "Untitled Recipe"));

    cookTimeLabel.setText("Cook time: " + or(r.getCookTime(), "N/A"));
    difficultyLabel.setText("Difficulty: " + or(r.getDifficulty(), "N/A"));
    servingsLabel.setText("Servings: " + (r.getServings() > 0 ? r.getServings() : "N/A"));

    int match = Math.max(0, Math.min(100, r.getPantryMatchPercent()));
    pantryMatchLabel.setText("✓ " + match + "% pantry match");
    pantryMatchProgressBar.setProgress(match / 100.0);

    List<String> pantry = r.getPantryIngredients() != null ? r.getPantryIngredients() : Collections.emptyList();
    List<String> missing = r.getMissingIngredients() != null ? r.getMissingIngredients() : Collections.emptyList();

    availableIngredientsListView.setItems(FXCollections.observableArrayList(pantry));
    missingIngredientsListView.setItems(FXCollections.observableArrayList(missing));

    String instructions = r.getInstructionsAsText();
    instructionsTextArea.setText(instructions == null || instructions.isBlank()
        ? "No instructions are available."
        : instructions);
  }

  private void loadRecipeImage(Recipe r) {
    if (recipeImageView == null)
      return;

    String storedUrl = r.getImageUrl();

    if (storedUrl != null && !storedUrl.isBlank()) {
      tryLoadImage(storedUrl, r.getSourceLink());
    } else {
      fetchOgImageThenLoad(r.getSourceLink());
    }
  }

  private void tryLoadImage(String url, String fallbackPageUrl) {
    try {
      Image img = new Image(url, true);
      img.errorProperty().addListener((obs, old, hasError) -> {
        if (hasError)
          fetchOgImageThenLoad(fallbackPageUrl);
      });
      recipeImageView.setImage(img);
      if (imagePlaceholderLabel != null)
        imagePlaceholderLabel.setVisible(false);
    } catch (Exception e) {
      fetchOgImageThenLoad(fallbackPageUrl);
    }
  }

  private void fetchOgImageThenLoad(String pageUrl) {
    if (pageUrl == null || pageUrl.isBlank()) {
      showPlaceholder();
      return;
    }
    Thread t = new Thread(() -> {
      String ogUrl = OgImageFetcher.fetch(pageUrl);
      Platform.runLater(() -> {
        if (ogUrl != null) {
          try {
            Image img = new Image(ogUrl, true);
            img.errorProperty().addListener((obs, old, err) -> {
              if (err)
                showPlaceholder();
            });
            recipeImageView.setImage(img);
            if (imagePlaceholderLabel != null)
              imagePlaceholderLabel.setVisible(false);
          } catch (Exception e) {
            showPlaceholder();
          }
        } else {
          showPlaceholder();
        }
      });
    }, "og-image-fetch");
    t.setDaemon(true);
    t.start();
  }

  private void showPlaceholder() {
    if (imagePlaceholderLabel != null)
      imagePlaceholderLabel.setVisible(true);
    if (recipeImageView != null)
      recipeImageView.setImage(null);
  }

  @FXML
  private void showIngredientsTab() {
    if (ingredientsContent == null)
      return;
    ingredientsContent.setVisible(true);
    ingredientsContent.setManaged(true);
    instructionsContent.setVisible(false);
    instructionsContent.setManaged(false);
    ingredientsTabLabel.setStyle(
        "-fx-text-fill: #2f6b4f; -fx-font-size: 12px; -fx-font-weight: bold;"
            + "-fx-border-color: transparent transparent #2f6b4f transparent;"
            + "-fx-border-width: 0 0 2 0; -fx-cursor: hand;");
    instructionsTabLabel.setStyle(
        "-fx-text-fill: #333333; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
  }

  @FXML
  private void showInstructionsTab() {
    if (instructionsContent == null)
      return;
    ingredientsContent.setVisible(false);
    ingredientsContent.setManaged(false);
    instructionsContent.setVisible(true);
    instructionsContent.setManaged(true);
    instructionsTabLabel.setStyle(
        "-fx-text-fill: #2f6b4f; -fx-font-size: 12px; -fx-font-weight: bold;"
            + "-fx-border-color: transparent transparent #2f6b4f transparent;"
            + "-fx-border-width: 0 0 2 0; -fx-cursor: hand;");
    ingredientsTabLabel.setStyle(
        "-fx-text-fill: #333333; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
  }

  @FXML
  private void handleHeartSaveRecipe() {
    if (recipe == null) {
      setStatus("No recipe is selected.", false);
      return;
    }
    if (!firebaseService.isConnected()) {
      setStatus("Not connected to Firebase.", false);
      return;
    }
    String uid = Session.getInstance().getUid();
    if (uid == null || uid.isBlank()) {
      setStatus("No user is currently signed in.", false);
      return;
    }

    recipe.setUserID(uid);
    if (heartButton != null)
      heartButton.setDisable(true);
    setStatus("Saving recipe...", true);

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.saveRecipe(recipe);
        return null;
      }
    };
    task.setOnSucceeded(e -> {
      if (heartButton != null) {
        heartButton.setText("♥");
        heartButton.setDisable(true);
      }
      setStatus("Recipe saved to your collection.", true);
    });
    task.setOnFailed(e -> {
      if (heartButton != null)
        heartButton.setDisable(false);
      setStatus("Failed to save: " + task.getException().getMessage(), false);
    });
    new Thread(task, "heart-save-recipe").start();
  }

  @FXML
  private void handleAddMissingToShoppingList() {
    if (recipe == null) {
      setStatus("No recipe is selected.", false);
      return;
    }

    String selected = missingIngredientsListView.getSelectionModel().getSelectedItem();
    if (selected == null || selected.isBlank()) {
      setStatus("Select a missing ingredient first.", false);
      return;
    }
    if (!firebaseService.isConnected()) {
      setStatus("Not connected to Firebase.", false);
      return;
    }
    String uid = Session.getInstance().getUid();
    if (uid == null || uid.isBlank()) {
      setStatus("No user is currently signed in.", false);
      return;
    }

    addMissingButton.setDisable(true);
    setStatus("Adding to shopping list...", true);

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.addShoppingItem(new ShoppingItem(selected, 1.0, "", uid));
        return null;
      }
    };
    task.setOnSucceeded(e -> {
      setStatus(selected + " added to shopping list.", true);
      missingIngredientsListView.getItems().remove(selected);
      if (recipe.getMissingIngredients() != null)
        recipe.getMissingIngredients().remove(selected);
      missingIngredientsListView.getSelectionModel().clearSelection();
      addMissingButton.setDisable(true);
      updateAddAllMissingButtonState();
    });
    task.setOnFailed(e -> {
      setStatus("Failed to add: " + task.getException().getMessage(), false);
      addMissingButton.setDisable(
          missingIngredientsListView.getSelectionModel().getSelectedItem() == null);
    });
    new Thread(task, "add-missing-to-shopping").start();
  }

  /**
   * Adds every ingredient currently in the "Missing" list to the shopping
   * list in one action.
   */
  @FXML
  private void handleAddAllMissingToShoppingList() {
    if (recipe == null) {
      setStatus("No recipe is selected.", false);
      return;
    }

    List<String> missing = new java.util.ArrayList<>(missingIngredientsListView.getItems());
    if (missing.isEmpty()) {
      setStatus("No missing ingredients to add.", true);
      return;
    }
    if (!firebaseService.isConnected()) {
      setStatus("Not connected to Firebase.", false);
      return;
    }
    String uid = Session.getInstance().getUid();
    if (uid == null || uid.isBlank()) {
      setStatus("No user is currently signed in.", false);
      return;
    }

    addMissingButton.setDisable(true);
    addAllMissingButton.setDisable(true);
    setStatus("Adding " + missing.size() + " item(s) to shopping list...", true);

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        for (String ingredientName : missing) {
          firebaseService.addShoppingItem(new ShoppingItem(ingredientName, 1.0, "", uid));
        }
        return null;
      }
    };
    task.setOnSucceeded(e -> {
      setStatus(missing.size() + " item(s) added to shopping list.", true);
      missingIngredientsListView.getItems().removeAll(missing);
      if (recipe.getMissingIngredients() != null)
        recipe.getMissingIngredients().removeAll(missing);
      missingIngredientsListView.getSelectionModel().clearSelection();
      addMissingButton.setDisable(true);
      updateAddAllMissingButtonState();
    });
    task.setOnFailed(e -> {
      setStatus("Failed to add items: " + task.getException().getMessage(), false);
      updateAddAllMissingButtonState();
    });
    new Thread(task, "add-all-missing-to-shopping").start();
  }

  private void updateAddAllMissingButtonState() {
    if (addAllMissingButton != null) {
      addAllMissingButton.setDisable(missingIngredientsListView.getItems().isEmpty());
    }
  }

  @FXML
  private void handleOpenSourceLink() {
    String url = recipe != null ? recipe.getSourceLink() : null;
    if (url == null || url.isBlank()) {
      setStatus("No source link available.", false);
      return;
    }
    try {
      if (Desktop.isDesktopSupported())
        Desktop.getDesktop().browse(new URI(url));
    } catch (Exception e) {
      setStatus("Could not open link: " + e.getMessage(), false);
    }
  }

  @FXML
  private void handleBackToRecipes() {
    navigateTo(Nav.Screen.RECIPES);
  }

  @FXML
  private void onNavPantry() {
    navigateTo(Nav.Screen.PANTRY);
  }

  @FXML
  private void onNavRecipes() {
    navigateTo(Nav.Screen.RECIPES);
  }

  @FXML
  private void onNavAdd() {
    navigateTo(Nav.Screen.ADD);
  }

  @FXML
  private void onNavShopping() {
    navigateTo(Nav.Screen.SHOPPING);
  }

  @FXML
  private void onNavProfile() {
    navigateTo(Nav.Screen.PROFILE);
  }

  private void navigateTo(Nav.Screen screen) {
    try {
      Nav.go((Stage) recipeNameLabel.getScene().getWindow(), screen);
    } catch (IOException e) {
      setStatus("Navigation error: " + e.getMessage(), false);
    }
  }

  private String or(String value, String fallback) {
    return (value == null || value.isBlank()) ? fallback : value;
  }

  private void setStatus(String message, boolean ok) {
    Platform.runLater(() -> {
      statusLabel.setText(message);
      statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    });
  }
}
