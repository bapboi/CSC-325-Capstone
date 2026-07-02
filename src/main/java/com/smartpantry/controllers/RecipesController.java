package com.smartpantry.controllers;

import com.smartpantry.data.AppData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

public class RecipesController {

    @FXML
    private ListView<String> recipeListView;

    @FXML
    private void initialize() {
        recipeListView.setItems(AppData.RECIPES);
        recipeListView.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleViewDetails(ActionEvent event) {
        String selectedRecipe = recipeListView.getSelectionModel().getSelectedItem();

        if (selectedRecipe == null) {
            Navigation.showInfo("No Recipe Selected", "Choose a recipe before viewing details.");
            return;
        }

        AppData.selectedRecipe = selectedRecipe;
        Navigation.goTo(event, "RecipeDetails.fxml");
    }

    @FXML
    private void handleSaveRecipe() {
        String selectedRecipe = recipeListView.getSelectionModel().getSelectedItem();

        if (selectedRecipe == null) {
            Navigation.showInfo("No Recipe Selected", "Choose a recipe before saving it.");
            return;
        }

        if (!AppData.SAVED_RECIPES.contains(selectedRecipe)) {
            AppData.SAVED_RECIPES.add(selectedRecipe);
        }

        Navigation.showInfo("Recipe Saved", selectedRecipe + " was added to saved recipes.");
    }

    @FXML
    private void handleBackToPantry(ActionEvent event) {
        Navigation.goTo(event, "Pantry.fxml");
    }
}
