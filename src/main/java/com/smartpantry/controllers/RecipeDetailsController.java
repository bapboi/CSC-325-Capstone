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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
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
    private Label statusLabel;

    @FXML
    private Label ingredientsTabLabel;

    @FXML
    private Label instructionsTabLabel;

    @FXML
    private VBox ingredientsContent;

    @FXML
    private VBox instructionsContent;

    private final FirebaseService firebaseService = FirebaseService.getInstance();
    private Recipe recipe;

    @FXML
    private void initialize() {
        recipe = SelectedRecipeStore.getSelectedRecipe();

        if (recipe == null) {
            statusLabel.setText("No recipe selected. Go back and choose a recipe.");
            addMissingButton.setDisable(true);
            heartButton.setDisable(true);
            return;
        }

        displayRecipe(recipe);
        loadRecipeImage(recipe);

        addMissingButton.setDisable(true);

        missingIngredientsListView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldItem, selectedItem) -> {
                    addMissingButton.setDisable(selectedItem == null);
                });

        showIngredientsTab();
    }

    private void displayRecipe(Recipe selectedRecipe) {
        recipeNameLabel.setText(useDefault(selectedRecipe.getName(), "Untitled Recipe"));

        cookTimeLabel.setText(
                "Cook time: " + useDefault(selectedRecipe.getCookTime(), "N/A")
        );

        difficultyLabel.setText(
                "Difficulty: " + useDefault(selectedRecipe.getDifficulty(), "N/A")
        );

        if (selectedRecipe.getServings() > 0) {
            servingsLabel.setText("Servings: " + selectedRecipe.getServings());
        } else {
            servingsLabel.setText("Servings: N/A");
        }

        int matchPercent = selectedRecipe.getPantryMatchPercent();

        if (matchPercent < 0) {
            matchPercent = 0;
        }

        if (matchPercent > 100) {
            matchPercent = 100;
        }

        pantryMatchLabel.setText("✓ " + matchPercent + "% pantry match");
        pantryMatchProgressBar.setProgress(matchPercent / 100.0);

        List<String> pantryIngredients = selectedRecipe.getPantryIngredients();

        if (pantryIngredients == null) {
            pantryIngredients = Collections.emptyList();
        }

        availableIngredientsListView.setItems(
                FXCollections.observableArrayList(pantryIngredients)
        );

        List<String> missingIngredients = selectedRecipe.getMissingIngredients();

        if (missingIngredients == null) {
            missingIngredients = Collections.emptyList();
        }

        missingIngredientsListView.setItems(
                FXCollections.observableArrayList(missingIngredients)
        );

        String instructions = selectedRecipe.getInstructionsAsText();

        if (instructions == null || instructions.isBlank()) {
            instructionsTextArea.setText("No instructions are available.");
        } else {
            instructionsTextArea.setText(instructions);
        }
    }

    @FXML
    private void showIngredientsTab() {
        ingredientsContent.setVisible(true);
        ingredientsContent.setManaged(true);

        instructionsContent.setVisible(false);
        instructionsContent.setManaged(false);

        ingredientsTabLabel.setStyle(
                "-fx-text-fill: #2f6b4f;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-color: transparent transparent #2f6b4f transparent;" +
                        "-fx-border-width: 0 0 2 0;" +
                        "-fx-cursor: hand;"
        );

        instructionsTabLabel.setStyle(
                "-fx-text-fill: #333333;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-cursor: hand;"
        );
    }

    @FXML
    private void showInstructionsTab() {
        ingredientsContent.setVisible(false);
        ingredientsContent.setManaged(false);

        instructionsContent.setVisible(true);
        instructionsContent.setManaged(true);

        instructionsTabLabel.setStyle(
                "-fx-text-fill: #2f6b4f;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-color: transparent transparent #2f6b4f transparent;" +
                        "-fx-border-width: 0 0 2 0;" +
                        "-fx-cursor: hand;"
        );

        ingredientsTabLabel.setStyle(
                "-fx-text-fill: #333333;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-cursor: hand;"
        );
    }

    @FXML
    private void handleAddMissingToShoppingList() {
        if (recipe == null) {
            setStatus("No recipe is selected.", false);
            return;
        }

        String selectedIngredient =
                missingIngredientsListView.getSelectionModel().getSelectedItem();

        if (selectedIngredient == null || selectedIngredient.isBlank()) {
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
        setStatus("Adding item to shopping list...", true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ShoppingItem item = new ShoppingItem(
                        selectedIngredient,
                        1.0,
                        "",
                        uid
                );

                firebaseService.addShoppingItem(item);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            setStatus(
                    selectedIngredient + " added to shopping list.",
                    true
            );

            missingIngredientsListView.getItems().remove(selectedIngredient);

            if (recipe.getMissingIngredients() != null) {
                recipe.getMissingIngredients().remove(selectedIngredient);
            }

            missingIngredientsListView.getSelectionModel().clearSelection();
            addMissingButton.setDisable(true);
        });

        task.setOnFailed(event -> {
            String message = task.getException() == null
                    ? "Unknown error"
                    : task.getException().getMessage();

            setStatus("Failed to add item: " + message, false);

            addMissingButton.setDisable(
                    missingIngredientsListView
                            .getSelectionModel()
                            .getSelectedItem() == null
            );
        });

        new Thread(task, "add-missing-to-shopping").start();
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
            Stage stage = (Stage) recipeNameLabel.getScene().getWindow();
            Nav.go(stage, screen);
        } catch (IOException exception) {
            exception.printStackTrace();
            setStatus("Navigation error: " + exception.getMessage(), false);
        }
    }

    private String useDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }

    private void setStatus(String message, boolean success) {
        Platform.runLater(() -> {
            statusLabel.setText(message);

            if (success) {
                statusLabel.setStyle("-fx-text-fill: #2e7d32;");
            } else {
                statusLabel.setStyle("-fx-text-fill: #c62828;");
            }
        });
    }

    private void loadRecipeImage(Recipe selectedRecipe) {
        String imageUrl = selectedRecipe.getImageUrl();

        if (imageUrl == null || imageUrl.isBlank()) {
            imagePlaceholderLabel.setVisible(true);
            recipeImageView.setImage(null);
            return;
        }

        try {
            Image recipeImage = new Image(imageUrl, true);

            recipeImage.errorProperty().addListener((observable, oldValue, hasError) -> {
                if (hasError) {
                    imagePlaceholderLabel.setVisible(true);
                    recipeImageView.setImage(null);
                }
            });

            recipeImageView.setImage(recipeImage);
            imagePlaceholderLabel.setVisible(false);

        } catch (Exception exception) {
            imagePlaceholderLabel.setVisible(true);
            recipeImageView.setImage(null);
        }
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

        heartButton.setDisable(true);
        setStatus("Saving recipe...", true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                firebaseService.saveRecipe(recipe);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            heartButton.setText("♥");
            heartButton.setDisable(true);
            setStatus("Recipe saved to your collection.", true);
        });

        task.setOnFailed(event -> {
            String message = task.getException() == null
                    ? "Unknown error"
                    : task.getException().getMessage();

            heartButton.setDisable(false);
            setStatus("Failed to save: " + message, false);
        });

        new Thread(task, "heart-save-recipe").start();
    }
}
