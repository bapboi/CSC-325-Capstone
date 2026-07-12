package com.smartpantry.controllers;

import com.smartpantry.model.Recipe;
import com.smartpantry.model.ShoppingItem;
import com.smartpantry.services.FirebaseService;
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
import javafx.stage.Stage;

import java.io.IOException;

public class RecipeDetailsController {

    @FXML private Label recipeNameLabel;
    @FXML private Label cookTimeLabel;
    @FXML private Label difficultyLabel;
    @FXML private Label servingsLabel;
    @FXML private Label pantryMatchLabel;
    @FXML private ProgressBar pantryMatchProgressBar;
    @FXML private ListView<String> availableIngredientsListView;
    @FXML private ListView<String> missingIngredientsListView;
    @FXML private TextArea instructionsTextArea;
    @FXML private Button addMissingButton;
    @FXML private Button saveButton;
    @FXML private Label statusLabel;

    private final FirebaseService firebaseService = FirebaseService.getInstance();
    private Recipe recipe;

    @FXML
    private void initialize() {
        recipe = SelectedRecipeStore.getSelectedRecipe();
        if (recipe == null) {
            statusLabel.setText("No recipe selected. Go back and choose a recipe.");
            addMissingButton.setDisable(true);
            saveButton.setDisable(true);
            return;
        }
        displayRecipe(recipe);
    }

    private void displayRecipe(Recipe r) {
        recipeNameLabel.setText(or(r.getName(), "Untitled Recipe"));
        cookTimeLabel.setText("Cook time: " + or(r.getCookTime(), "N/A"));
        difficultyLabel.setText("Difficulty: " + or(r.getDifficulty(), "N/A"));
        servingsLabel.setText("Servings: " + (r.getServings() > 0 ? r.getServings() : "N/A"));

        int match = r.getPantryMatchPercent();
        pantryMatchLabel.setText("Pantry match: " + match + "%");
        pantryMatchProgressBar.setProgress(match / 100.0);

        availableIngredientsListView.setItems(
                FXCollections.observableArrayList(r.getPantryIngredients()));
        missingIngredientsListView.setItems(
                FXCollections.observableArrayList(r.getMissingIngredients()));

        instructionsTextArea.setText(r.getInstructionsAsText());
        addMissingButton.setDisable(r.getMissingIngredients().isEmpty());
    }

    @FXML
    private void handleSaveRecipe() {
        if (recipe == null) return;
        if (!firebaseService.isConnected()) {
            setStatus("Not connected to Firebase.", false);
            return;
        }
        saveButton.setDisable(true);
        setStatus("Saving recipe...", true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                firebaseService.saveRecipe(recipe);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setStatus("Recipe saved to your collection.", true);
            saveButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            setStatus("Failed to save: " + task.getException().getMessage(), false);
            saveButton.setDisable(false);
        });
        new Thread(task, "save-recipe").start();
    }

    @FXML
    private void handleAddMissingToShoppingList() {
        if (recipe == null || recipe.getMissingIngredients().isEmpty()) {
            setStatus("No missing ingredients to add.", false);
            return;
        }
        if (!firebaseService.isConnected()) {
            setStatus("Not connected to Firebase.", false);
            return;
        }
        addMissingButton.setDisable(true);
        setStatus("Adding missing ingredients to shopping list...", true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String uid = Session.getInstance().getUid();
                for (String name : recipe.getMissingIngredients()) {
                    firebaseService.addShoppingItem(new ShoppingItem(name, 1.0, "", uid));
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setStatus(recipe.getMissingIngredients().size() + " item(s) added to shopping list.", true);
            addMissingButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            setStatus("Failed to add items: " + task.getException().getMessage(), false);
            addMissingButton.setDisable(false);
        });
        new Thread(task, "add-missing-to-shopping").start();
    }

    @FXML
    private void handleBackToRecipes() {
        try {
            Nav.go((Stage) recipeNameLabel.getScene().getWindow(), Nav.Screen.RECIPES);
        } catch (IOException e) {
            setStatus("Navigation error: " + e.getMessage(), false);
        }
    }

    private String or(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private void setStatus(String msg, boolean ok) {
        Platform.runLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
        });
    }
}
