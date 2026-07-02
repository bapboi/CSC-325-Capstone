package com.smartpantry.controllers;

import com.smartpantry.data.AppData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

public class PantryController {

    @FXML
    private ListView<String> pantryListView;

    @FXML
    private void initialize() {
        pantryListView.setItems(AppData.PANTRY_ITEMS);
    }

    @FXML
    private void handleAddIngredient(ActionEvent event) {
        Navigation.goTo(event, "AddIngredient.fxml");
    }

    @FXML
    private void handleRecipes(ActionEvent event) {
        Navigation.goTo(event, "Recipes.fxml");
    }

    @FXML
    private void handleSavedRecipes(ActionEvent event) {
        Navigation.goTo(event, "SavedRecipes.fxml");
    }

    @FXML
    private void handleAIDetection(ActionEvent event) {
        Navigation.goTo(event, "AIDetection.fxml");
    }

    @FXML
    private void handleShoppingList(ActionEvent event) {
        Navigation.goTo(event, "ShoppingList.fxml");
    }

    @FXML
    private void handleProfile(ActionEvent event) {
        Navigation.goTo(event, "Profile.fxml");
    }

    @FXML
    private void handleSettings(ActionEvent event) {
        Navigation.goTo(event, "Settings.fxml");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        Navigation.goTo(event, "Login.fxml");
    }
}
