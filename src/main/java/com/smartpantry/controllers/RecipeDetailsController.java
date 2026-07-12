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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;

public class RecipeDetailsController {

    @FXML
    private Label recipeNameLabel;

    @FXML
    private ImageView recipeImageView;

    @FXML
    private Label imagePlaceholderLabel;

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
    private Label statusLabel;

    private final FirebaseService firebaseService = FirebaseService.getInstance();
    private Recipe recipe;

    @FXML
    private void initialize() {
        recipe = SelectedRecipeStore.getSelectedRecipe();

        if (recipe == null) {
            recipe = createPlaceholderRecipe();
            statusLabel.setText("Showing placeholder recipe. Select a recipe from the Recipes screen for real details.");
        }

        displayRecipe(recipe);
    }

    private void displayRecipe(Recipe recipe) {
        recipeNameLabel.setText(valueOrDefault(recipe.getName(), "Untitled Recipe"));

        cookTimeLabel.setText("Cook time: " + valueOrDefault(recipe.getCookTime(), "N/A"));
        difficultyLabel.setText("Difficulty: " + valueOrDefault(recipe.getDifficulty(), "N/A"));
        servingsLabel.setText("Servings: " + (recipe.getServings() > 0 ? recipe.getServings() : "N/A"));

        int matchPercent = recipe.getPantryMatchPercent();
        pantryMatchLabel.setText("Pantry match: " + matchPercent + "%");
        pantryMatchProgressBar.setProgress(matchPercent / 100.0);

        availableIngredientsListView.setItems(
                FXCollections.observableArrayList(recipe.getPantryIngredients())
        );

        missingIngredientsListView.setItems(
                FXCollections.observableArrayList(recipe.getMissingIngredients())
        );

        instructionsTextArea.setText(recipe.getInstructionsAsText());

        addMissingButton.setDisable(recipe.getMissingIngredients().isEmpty());

        loadRecipeImage(recipe.getImageUrl());
    }

    private void loadRecipeImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            imagePlaceholderLabel.setVisible(true);
            recipeImageView.setImage(null);
            return;
        }

        try {
            Image image = new Image(imageUrl, true);
            recipeImageView.setImage(image);
            imagePlaceholderLabel.setVisible(false);
        } catch (Exception e) {
            recipeImageView.setImage(null);
            imagePlaceholderLabel.setVisible(true);
        }
    }

    @FXML
    private void handleAddMissingToShoppingList() {
        if (recipe == null || recipe.getMissingIngredients().isEmpty()) {
            statusLabel.setText("There are no missing ingredients to add.");
            return;
        }

        if (!firebaseService.isConnected()) {
            statusLabel.setText("Not connected to Firebase.");
            return;
        }

        addMissingButton.setDisable(true);
        statusLabel.setText("Adding missing ingredients...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String uid = Session.getInstance().getUid();

                for (String ingredientName : recipe.getMissingIngredients()) {
                    ShoppingItem item = new ShoppingItem(ingredientName, 1.0, "", uid);
                    firebaseService.addShoppingItem(item);
                }

                return null;
            }
        };

        task.setOnSucceeded(event -> {
            statusLabel.setText("Missing ingredients added to shopping list.");
            addMissingButton.setDisable(false);
        });

        task.setOnFailed(event -> {
            statusLabel.setText("Failed to add items: " + task.getException().getMessage());
            addMissingButton.setDisable(false);
        });

        new Thread(task, "add-missing-recipe-items").start();
    }

    @FXML
    private void handleBackToRecipes() {
        try {
            Nav.go((Stage) recipeNameLabel.getScene().getWindow(), Nav.Screen.RECIPES);
        } catch (IOException e) {
            statusLabel.setText("Failed to load recipes screen: " + e.getMessage());
        }
    }

    private Recipe createPlaceholderRecipe() {
        Recipe placeholder = new Recipe("Chicken Rice Bowl");
        placeholder.setCookTime("30 min");
        placeholder.setDifficulty("Easy");
        placeholder.setServings(2);
        placeholder.setPantryIngredients(java.util.List.of("Rice", "Garlic"));
        placeholder.setMissingIngredients(java.util.List.of("Chicken Breast", "Spinach"));
        placeholder.setIngredients(java.util.List.of("Rice", "Garlic", "Chicken Breast", "Spinach"));
        placeholder.setInstructions(java.util.List.of(
                "Cook the rice according to package directions.",
                "Season and cook the chicken until done.",
                "Sauté garlic and spinach.",
                "Combine everything in a bowl and serve."
        ));
        return placeholder;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}