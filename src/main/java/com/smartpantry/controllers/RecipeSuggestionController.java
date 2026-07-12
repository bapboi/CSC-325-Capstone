package com.smartpantry.controllers;

import com.smartpantry.model.Ingredient;
import com.smartpantry.model.Recipe;
import com.smartpantry.model.ShoppingItem;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.GeminiRecipeService;
import com.smartpantry.services.SelectedRecipeStore;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeSuggestionController {

  @FXML
  private Button findButton;
  @FXML
  private Button addMissingButton;
  @FXML
  private Button viewDetailsButton;
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

  private List<String> lastMissingIngredients = List.of();
  private List<String> lastPantryIngredients = List.of();

  private Recipe currentRecipe;

  @FXML
  public void initialize() {
    progressIndicator.setVisible(false);
    resultLink.setVisible(false);
    addMissingButton.setVisible(false);
    viewDetailsButton.setVisible(false);
    viewDetailsButton.setManaged(false);
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

    currentRecipe = null;
    lastMissingIngredients = List.of();
    lastPantryIngredients = List.of();

    viewDetailsButton.setVisible(false);
    viewDetailsButton.setManaged(false);
    viewDetailsButton.setDisable(true);

    setStatus("Reading your pantry...", true);

    Task<GeminiRecipeService.RecipeResult> task = new Task<>() {
      @Override
      protected GeminiRecipeService.RecipeResult call() throws Exception {
        List<Ingredient> pantry = firebaseService.getAllIngredients();
        if (pantry.isEmpty()) throw new IllegalStateException("Your pantry is empty — add some ingredients first.");

        List<String> names = pantry.stream()
                .map(Ingredient::getName)
                .collect(Collectors.toList());

        lastPantryIngredients = names;

        return geminiService.findRecipe(names);
      }
    };

    task.setOnSucceeded(e -> {
      GeminiRecipeService.RecipeResult result = task.getValue();
      resultLabel.setText(result.description());

      currentRecipe = new Recipe(extractRecipeName(result.description()));
      currentRecipe.setSourceLink(result.link());
      currentRecipe.setCookTime("30 min");
      currentRecipe.setDifficulty("Medium");
      currentRecipe.setServings(2);
      currentRecipe.setPantryIngredients(new ArrayList<>(lastPantryIngredients));
      currentRecipe.setInstructions(List.of(
              result.description(),
              "Review the listed ingredients.",
              "Prepare the ingredients and follow the recipe source if available."
      ));

      viewDetailsButton.setVisible(true);
      viewDetailsButton.setManaged(true);
      viewDetailsButton.setDisable(true);

      if (result.link() != null && !result.link().isBlank()) {
        resultLink.setText(result.link());
        resultLink.setVisible(true);
      }

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

      if (currentRecipe != null) {
        currentRecipe.setMissingIngredients(new ArrayList<>(lastMissingIngredients));

        List<String> allIngredients = new ArrayList<>();
        allIngredients.addAll(currentRecipe.getPantryIngredients());
        allIngredients.addAll(lastMissingIngredients);
        currentRecipe.setIngredients(allIngredients);
      }

      if (!lastMissingIngredients.isEmpty()) {
        addMissingButton.setText("Add " + lastMissingIngredients.size() + " missing to Shopping List");
        addMissingButton.setVisible(true);
      }

      viewDetailsButton.setDisable(false);
    });
    task.setOnFailed(e -> {
      viewDetailsButton.setDisable(false);
    });
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

  // inline nav
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

  @FXML
  private void onViewDetails() {
    if (currentRecipe == null) {
      setStatus("Find a recipe before viewing details.", false);
      return;
    }

    SelectedRecipeStore.setSelectedRecipe(currentRecipe);
    nav(Nav.Screen.RECIPE_DETAILS);
  }

  private String extractRecipeName(String description) {
    if (description == null || description.isBlank()) {
      return "Suggested Recipe";
    }

    String firstLine = description.split("\\R")[0].trim();

    if (firstLine.length() > 60) {
      return firstLine.substring(0, 60) + "...";
    }

    return firstLine;
  }
}
