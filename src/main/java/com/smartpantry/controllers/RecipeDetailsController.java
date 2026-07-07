package com.smartpantry.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.IOException;

public class RecipeDetailsController {

    @FXML
    private Label recipeTitleLabel;

    @FXML
    private TextArea ingredientsTextArea;

    @FXML
    private TextArea instructionsTextArea;

    // Temporary selected recipe until this screen is connected to RecipeSuggestionController/Firebase.
    private static String selectedRecipeName = "Chicken Rice Bowl";

    public static void setSelectedRecipeName(String recipeName) {
        if (recipeName != null && !recipeName.isBlank()) {
            selectedRecipeName = recipeName;
        }
    }

    @FXML
    private void initialize() {
        recipeTitleLabel.setText(selectedRecipeName);
        ingredientsTextArea.setText(getIngredientsFor(selectedRecipeName));
        instructionsTextArea.setText(getInstructionsFor(selectedRecipeName));
    }

    @FXML
    private void handleSaveRecipe() {
        showInfo("Recipe Saved", selectedRecipeName + " was marked as saved.");
    }

    @FXML
    private void handleAddMissingToShoppingList() {
        showInfo(
                "Shopping List Updated",
                "Missing ingredients for " + selectedRecipeName + " can be added once shopping list storage is connected."
        );
    }

    @FXML
    private void handleBackToRecipes() {
        try {
            Nav.go((Stage) recipeTitleLabel.getScene().getWindow(), Nav.Screen.RECIPES);
        } catch (IOException e) {
            showInfo("Navigation Error", "Failed to load recipes screen: " + e.getMessage());
        }
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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}