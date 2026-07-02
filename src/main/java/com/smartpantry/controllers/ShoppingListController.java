package com.smartpantry.controllers;

import com.smartpantry.data.AppData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class ShoppingListController {

    @FXML
    private ListView<String> shoppingListView;

    @FXML
    private TextField itemField;

    @FXML
    private void initialize() {
        shoppingListView.setItems(AppData.SHOPPING_LIST);
    }

    @FXML
    private void handleAddItem() {
        String item = itemField.getText().trim();

        if (item.isEmpty()) {
            Navigation.showInfo("Missing Item", "Enter an item before adding it.");
            return;
        }

        AppData.SHOPPING_LIST.add(item);
        itemField.clear();
    }

    @FXML
    private void handleMarkPurchased() {
        String selectedItem = shoppingListView.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            Navigation.showInfo("No Item Selected", "Choose an item to mark as purchased.");
            return;
        }

        AppData.SHOPPING_LIST.remove(selectedItem);
    }

    @FXML
    private void handleBackToPantry(ActionEvent event) {
        Navigation.goTo(event, "Pantry.fxml");
    }
}
