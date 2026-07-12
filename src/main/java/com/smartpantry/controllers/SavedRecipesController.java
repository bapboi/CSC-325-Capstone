package com.smartpantry.controllers;

import com.smartpantry.model.Recipe;
import com.smartpantry.services.SelectedRecipeStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class SavedRecipesController {

    @FXML
    private ListView<String> savedRecipeListView;

    private final ObservableList<String> savedRecipes = FXCollections.observableArrayList(
            "Chicken Rice Bowl",
            "Spinach Smoothie",
            "Garlic Fried Rice"
    );

    @FXML
    private void initialize() {
        savedRecipeListView.setItems(savedRecipes);
        savedRecipeListView.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleViewDetails() {
        String selectedRecipe = savedRecipeListView.getSelectionModel().getSelectedItem();

        if (selectedRecipe == null) {
            showInfo("No Recipe Selected", "Choose a saved recipe before viewing details.");
            return;
        }

        Recipe recipe = createRecipeFromSavedName(selectedRecipe);
        SelectedRecipeStore.setSelectedRecipe(recipe);

        try {
            Nav.go((Stage) savedRecipeListView.getScene().getWindow(), Nav.Screen.RECIPE_DETAILS);
        } catch (IOException e) {
            showInfo("Navigation Error", "Failed to load recipe details screen: " + e.getMessage());
        }
    }

    @FXML
    private void handleRemoveSavedRecipe() {
        String selectedRecipe = savedRecipeListView.getSelectionModel().getSelectedItem();

        if (selectedRecipe == null) {
            showInfo("No Recipe Selected", "Choose a recipe before removing it.");
            return;
        }

        savedRecipes.remove(selectedRecipe);
    }

    @FXML
    private void handleBackToPantry() {
        try {
            Nav.go((Stage) savedRecipeListView.getScene().getWindow(), Nav.Screen.PANTRY);
        } catch (IOException e) {
            showInfo("Navigation Error", "Failed to load pantry screen: " + e.getMessage());
        }
    }

    private Recipe createRecipeFromSavedName(String recipeName) {
        Recipe recipe = new Recipe(recipeName);

        recipe.setCookTime("30 min");
        recipe.setDifficulty("Easy");
        recipe.setServings(2);

        recipe.setPantryIngredients(List.of("Rice", "Garlic"));
        recipe.setMissingIngredients(List.of("Chicken Breast", "Spinach"));
        recipe.setIngredients(List.of("Rice", "Garlic", "Chicken Breast", "Spinach"));

        recipe.setInstructions(List.of(
                "Prepare all ingredients.",
                "Cook the main ingredients until done.",
                "Combine everything and serve."
        ));

        return recipe;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}