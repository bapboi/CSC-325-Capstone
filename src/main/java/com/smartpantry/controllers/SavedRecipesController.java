package com.smartpantry.controllers;

import com.smartpantry.model.Recipe;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.SelectedRecipeStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class SavedRecipesController {

    @FXML
    private ListView<Recipe> savedRecipeListView;

    @FXML
    private Label statusLabel;

    private final FirebaseService firebaseService =
            FirebaseService.getInstance();

    private final ObservableList<Recipe> recipes =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        savedRecipeListView.setItems(recipes);
        savedRecipeListView.setFixedCellSize(-1);
        savedRecipeListView.setFocusTraversable(false);
        savedRecipeListView.setCellFactory(listView -> new RecipeCell());
        onRefresh();
    }

    @FXML
    private void onRefresh() {
        if (!firebaseService.isConnected()) {
            setStatus("Unable to connect to Firebase.", false);
            return;
        }

        setStatus("Loading saved recipes...", true);

        Task<List<Recipe>> task = new Task<>() {
            @Override
            protected List<Recipe> call() throws Exception {
                return firebaseService.getSavedRecipes();
            }
        };

        task.setOnSucceeded(event -> {
            List<Recipe> savedRecipes = task.getValue();

            if (savedRecipes == null) {
                recipes.clear();
            } else {
                recipes.setAll(savedRecipes);
            }

            updateRecipeCount();
        });

        task.setOnFailed(event -> {
            setStatus(
                    "Unable to load saved recipes: "
                            + getErrorMessage(task.getException()),
                    false
            );
        });

        startTask(task, "load-saved-recipes");
    }

    @FXML
    private void handleViewDetails() {
        Recipe selectedRecipe =
                savedRecipeListView.getSelectionModel().getSelectedItem();

        if (selectedRecipe == null) {
            setStatus("Select a recipe to view.", false);
            return;
        }

        SelectedRecipeStore.setSelectedRecipe(selectedRecipe);
        navigateTo(Nav.Screen.RECIPE_DETAILS);
    }

    @FXML
    private void handleRemoveSavedRecipe() {
        Recipe selectedRecipe =
                savedRecipeListView.getSelectionModel().getSelectedItem();

        if (selectedRecipe == null) {
            setStatus("Select a recipe to remove.", false);
            return;
        }

        if (selectedRecipe.getId() == null
                || selectedRecipe.getId().isBlank()) {
            setStatus("This recipe could not be removed.", false);
            return;
        }

        setStatus("Removing recipe...", true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                firebaseService.deleteSavedRecipe(selectedRecipe.getId());
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            recipes.remove(selectedRecipe);
            savedRecipeListView.getSelectionModel().clearSelection();
            updateRecipeCount();
        });

        task.setOnFailed(event -> {
            setStatus(
                    "Unable to remove recipe: "
                            + getErrorMessage(task.getException()),
                    false
            );
        });

        startTask(task, "remove-saved-recipe");
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
            Stage stage =
                    (Stage) savedRecipeListView.getScene().getWindow();

            Nav.go(stage, screen);
        } catch (IOException exception) {
            setStatus(
                    "Unable to open that screen: "
                            + getErrorMessage(exception),
                    false
            );
        }
    }

    private void updateRecipeCount() {
        int count = recipes.size();

        if (count == 0) {
            setStatus("You do not have any saved recipes yet.", true);
        } else if (count == 1) {
            setStatus("1 saved recipe", true);
        } else {
            setStatus(count + " saved recipes", true);
        }
    }

    private void startTask(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private String getErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "An unexpected error occurred.";
        }

        if (throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
    }

    private void setStatus(String message, boolean success) {
        statusLabel.setText(message);

        if (success) {
            statusLabel.setStyle(
                    "-fx-font-size: 12px;"
                            + "-fx-text-fill: #4F6559;"
            );
        } else {
            statusLabel.setStyle(
                    "-fx-font-size: 12px;"
                            + "-fx-font-weight: bold;"
                            + "-fx-text-fill: #B3261E;"
            );
        }
    }

    private static class RecipeCell extends ListCell<Recipe> {

        @Override
        protected void updateItem(Recipe recipe, boolean empty) {
            super.updateItem(recipe, empty);

            if (empty || recipe == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            String recipeName = recipe.getName();

            if (recipeName == null || recipeName.isBlank()) {
                recipeName = "Unnamed Recipe";
            }

            recipeName = recipeName.replace("**", "");

            Label nameLabel = new Label(recipeName);
            nameLabel.setWrapText(true);
            nameLabel.setPrefWidth(250);

            nameLabel.setStyle(
                    "-fx-font-size: 13px;"
                            + "-fx-font-weight: bold;"
                            + "-fx-text-fill: #26352D;"
            );

            Label detailsLabel = new Label(createDetailsText(recipe));
            detailsLabel.setWrapText(true);
            detailsLabel.setPrefWidth(250);

            detailsLabel.setStyle(
                    "-fx-font-size: 11px;"
                            + "-fx-text-fill: #6A756E;"
            );

            VBox content = new VBox(4);
            content.getChildren().addAll(nameLabel, detailsLabel);
            content.setFillWidth(true);
            content.setPrefWidth(260);
            content.setMaxWidth(260);
            content.setStyle("-fx-padding: 10 8;");

            setText(null);
            setGraphic(content);
        }

        private String createDetailsText(Recipe recipe) {
            String details = "";

            if (recipe.getPantryMatchPercent() > 0) {
                details = recipe.getPantryMatchPercent()
                        + "% pantry match";
            }

            String totalTime = recipe.getTotalTime();

            if (totalTime != null
                    && !totalTime.isBlank()
                    && !totalTime.equals("N/A")) {

                if (!details.isEmpty()) {
                    details += "  •  ";
                }

                details += totalTime;
            }

            if (details.isEmpty()) {
                details = "Saved recipe";
            }

            return details;
        }
    }
}