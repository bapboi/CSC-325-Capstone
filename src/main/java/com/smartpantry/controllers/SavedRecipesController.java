package com.smartpantry.controllers;

import com.smartpantry.data.AppData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

public class SavedRecipesController {

    @FXML
    private ListView<String> savedRecipeListView;

    @FXML
    private void initialize() {
        savedRecipeListView.setItems(AppData.SAVED_RECIPES);
        savedRecipeListView.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleViewDetails(ActionEvent event) {
        String selectedRecipe = savedRecipeListView.getSelectionModel().getSelectedItem();

        if (selectedRecipe == null) {
            Navigation.showInfo("No Recipe Selected", "Choose a saved recipe before viewing details.");
            return;
        }

        AppData.selectedRecipe = selectedRecipe;
        Navigation.goTo(event, "RecipeDetails.fxml");
    }

    @FXML
    private void handleRemoveSavedRecipe() {
        String selectedRecipe = savedRecipeListView.getSelectionModel().getSelectedItem();

        if (selectedRecipe == null) {
            Navigation.showInfo("No Recipe Selected", "Choose a recipe before removing it.");
            return;
        }

        AppData.SAVED_RECIPES.remove(selectedRecipe);
    }

    @FXML
    private void handleBackToPantry(ActionEvent event) {
        Navigation.goTo(event, "Pantry.fxml");
    }
}
