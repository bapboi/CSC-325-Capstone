package com.smartpantry.controllers;

import com.smartpantry.data.AppData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class RecipeDetailsController {

    @FXML
    private Label recipeTitleLabel;

    @FXML
    private TextArea ingredientsTextArea;

    @FXML
    private TextArea instructionsTextArea;

    @FXML
    private void initialize() {
        String recipeName = AppData.selectedRecipe;
        recipeTitleLabel.setText(recipeName);
        ingredientsTextArea.setText(getIngredientsFor(recipeName));
        instructionsTextArea.setText(getInstructionsFor(recipeName));
    }

    @FXML
    private void handleSaveRecipe() {
        String recipeName = AppData.selectedRecipe;

        if (!AppData.SAVED_RECIPES.contains(recipeName)) {
            AppData.SAVED_RECIPES.add(recipeName);
        }

        Navigation.showInfo("Recipe Saved", recipeName + " was added to saved recipes.");
    }

    @FXML
    private void handleAddMissingToShoppingList() {
        AppData.SHOPPING_LIST.add("Missing ingredients for " + AppData.selectedRecipe);
        Navigation.showInfo("Shopping List Updated", "Missing ingredients were added to the shopping list.");
    }

    @FXML
    private void handleBackToRecipes(ActionEvent event) {
        Navigation.goTo(event, "Recipes.fxml");
    }

    private String getIngredientsFor(String recipeName) {
        return switch (recipeName) {
            case "Chicken Rice Bowl" -> "Chicken Breast\nRice\nSpinach\nGarlic";
            case "Spinach Smoothie" -> "Spinach\nMilk\nBanana\nIce";
            case "Garlic Fried Rice" -> "Rice\nGarlic\nEggs\nSoy Sauce";
            default -> "Eggs\nMilk\nSpinach\nSalt";
        };
    }

    private String getInstructionsFor(String recipeName) {
        return switch (recipeName) {
            case "Chicken Rice Bowl" -> "Cook rice, grill chicken, then combine with spinach and garlic.";
            case "Spinach Smoothie" -> "Blend spinach, milk, banana, and ice until smooth.";
            case "Garlic Fried Rice" -> "Fry garlic, add rice and eggs, then season.";
            default -> "Whisk eggs and milk, add spinach, then cook in a pan.";
        };
    }
}
