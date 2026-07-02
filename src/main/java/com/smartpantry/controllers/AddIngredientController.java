package com.smartpantry.controllers;

import com.smartpantry.data.AppData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class AddIngredientController {

    @FXML
    private TextField ingredientNameField;

    @FXML
    private TextField quantityField;

    @FXML
    private ComboBox<String> categoryComboBox;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        categoryComboBox.getItems().addAll("Dairy", "Meat", "Produce", "Grain", "Frozen", "Other");
        categoryComboBox.getSelectionModel().select("Other");
    }

    @FXML
    private void handleAddIngredient() {
        String name = ingredientNameField.getText().trim();
        String quantity = quantityField.getText().trim();
        String category = categoryComboBox.getValue();

        if (name.isEmpty() || quantity.isEmpty()) {
            statusLabel.setText("Enter an ingredient name and quantity.");
            return;
        }

        AppData.PANTRY_ITEMS.add(name + " - " + quantity + " (" + category + ")");
        ingredientNameField.clear();
        quantityField.clear();
        statusLabel.setText("Ingredient added to pantry.");
    }

    @FXML
    private void handleBackToPantry(ActionEvent event) {
        Navigation.goTo(event, "Pantry.fxml");
    }
}
