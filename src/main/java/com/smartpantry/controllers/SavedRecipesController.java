package com.smartpantry.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.io.IOException;

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

        RecipeDetailsController.setSelectedRecipeName(selectedRecipe);

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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}