package com.smartpantry.controllers;

import com.smartpantry.model.Recipe;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.SelectedRecipeStore;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class SavedRecipesController {

    @FXML private ListView<Recipe> savedRecipeListView;
    @FXML private Label statusLabel;

    private final FirebaseService firebaseService = FirebaseService.getInstance();
    private final ObservableList<Recipe> recipes = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        savedRecipeListView.setItems(recipes);
        savedRecipeListView.setCellFactory(lv -> new RecipeCell());
        onRefresh();
    }

    @FXML
    private void onRefresh() {
        if (!firebaseService.isConnected()) {
            setStatus("Not connected to Firebase.", false);
            return;
        }
        setStatus("Loading saved recipes...", true);

        Task<List<Recipe>> task = new Task<>() {
            @Override
            protected List<Recipe> call() throws Exception {
                return firebaseService.getSavedRecipes();
            }
        };
        task.setOnSucceeded(e -> {
            recipes.setAll(task.getValue());
            setStatus(recipes.size() + " saved recipe(s)", true);
        });
        task.setOnFailed(e -> setStatus("Failed to load: " + task.getException().getMessage(), false));
        new Thread(task, "load-saved-recipes").start();
    }

    @FXML
    private void handleViewDetails() {
        Recipe selected = savedRecipeListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a recipe to view.", false);
            return;
        }
        SelectedRecipeStore.setSelectedRecipe(selected);
        try {
            Nav.go((Stage) savedRecipeListView.getScene().getWindow(), Nav.Screen.RECIPE_DETAILS);
        } catch (IOException e) {
            setStatus("Failed to load recipe details: " + e.getMessage(), false);
        }
    }

    @FXML
    private void handleRemoveSavedRecipe() {
        Recipe selected = savedRecipeListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getId() == null) {
            setStatus("Select a recipe to remove.", false);
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                firebaseService.deleteSavedRecipe(selected.getId());
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setStatus("Recipe removed.", true);
            onRefresh();
        });
        task.setOnFailed(e -> setStatus("Failed to remove: " + task.getException().getMessage(), false));
        new Thread(task, "remove-saved-recipe").start();
    }

    @FXML
    private void handleBackToPantry() {
        try {
            Nav.go((Stage) savedRecipeListView.getScene().getWindow(), Nav.Screen.PANTRY);
        } catch (IOException e) {
            setStatus("Navigation error: " + e.getMessage(), false);
        }
    }

    private static class RecipeCell extends ListCell<Recipe> {
        @Override
        protected void updateItem(Recipe item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); return; }
            String match = item.getPantryMatchPercent() > 0
                    ? " — " + item.getPantryMatchPercent() + "% pantry match" : "";
            String time = item.getCookTime() != null ? " · " + item.getCookTime() : "";
            setText(item.getName() + match + time);
        }
    }

    private void setStatus(String msg, boolean ok) {
        Platform.runLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
        });
    }
}
