package com.smartpantry.controllers;

import com.smartpantry.model.Ingredient;
import com.smartpantry.model.ShoppingItem;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.Session;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

public class ShoppingListController {

  @FXML
  private TextField nameField;

  @FXML
  private TextField quantityField;

  @FXML
  private TextField unitField;

  @FXML
  private Label statusLabel;

  @FXML
  private ListView<ShoppingItem> listView;

  private final FirebaseService firebaseService = FirebaseService.getInstance();
  private final ObservableList<ShoppingItem> items = FXCollections.observableArrayList();

  @FXML
  public void initialize() {
    listView.setItems(items);
    listView.setCellFactory(list -> new ShoppingCell());
    onRefresh();
  }

  @FXML
  private void onRefresh() {
    if (!firebaseService.isConnected()) {
      setStatus("Not connected to Firebase", false);
      return;
    }

    Task<List<ShoppingItem>> task = new Task<>() {
      @Override
      protected List<ShoppingItem> call() throws Exception {
        return firebaseService.getShoppingList();
      }
    };

    task.setOnSucceeded(event -> {
      items.setAll(task.getValue());
      updateItemCount();
    });

    task.setOnFailed(event -> {
      setStatus("Load failed: " + task.getException().getMessage(), false);
    });

    Thread thread = new Thread(task, "shopping-load");
    thread.setDaemon(true);
    thread.start();
  }

  @FXML
  private void onAdd() {
    String name = nameField.getText();

    if (name == null || name.isBlank()) {
      setStatus("Enter an item name", false);
      return;
    }

    String cleanedName = name.trim();

    if (alreadyInShoppingList(cleanedName)) {
      setStatus("\"" + cleanedName + "\" is already in your shopping list", false);
      return;
    }

    double quantity;

    try {
      String quantityText = quantityField.getText();

      if (quantityText == null || quantityText.isBlank()) {
        quantity = 1.0;
      } else {
        quantity = Double.parseDouble(quantityText);
      }
    } catch (NumberFormatException exception) {
      setStatus("Quantity must be a number", false);
      return;
    }

    String unit = unitField.getText();

    if (unit == null) {
      unit = "";
    }

    ShoppingItem item = new ShoppingItem(
            cleanedName,
            quantity,
            unit.trim(),
            Session.getInstance().getUid()
    );

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.addShoppingItem(item);
        return null;
      }
    };

    task.setOnSucceeded(event -> {
      nameField.clear();
      quantityField.setText("1");
      unitField.clear();
      onRefresh();
    });

    task.setOnFailed(event -> {
      setStatus("Add failed: " + task.getException().getMessage(), false);
    });

    Thread thread = new Thread(task, "shopping-add");
    thread.setDaemon(true);
    thread.start();
  }

  private boolean alreadyInShoppingList(String newItemName) {
    for (ShoppingItem item : items) {
      String currentItemName = item.getName();

      if (currentItemName != null
              && currentItemName.trim().equalsIgnoreCase(newItemName.trim())) {
        return true;
      }
    }

    return false;
  }

  @FXML
  private void onToggleChecked() {
    ShoppingItem selectedItem = listView.getSelectionModel().getSelectedItem();

    if (selectedItem == null || selectedItem.getId() == null) {
      setStatus("Select an item first", false);
      return;
    }

    boolean newCheckedValue = !selectedItem.isChecked();

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.updateShoppingItem(
                selectedItem.getId(),
                Map.of("checked", newCheckedValue)
        );
        return null;
      }
    };

    task.setOnSucceeded(event -> onRefresh());

    task.setOnFailed(event -> {
      setStatus("Update failed: " + task.getException().getMessage(), false);
    });

    Thread thread = new Thread(task, "shopping-toggle");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * moves the selected checked item into pantryItems then removes it from the
   * shopping list
   */
  @FXML
  private void onMoveToPantry() {
    ShoppingItem selectedItem = listView.getSelectionModel().getSelectedItem();

    if (selectedItem == null || selectedItem.getId() == null) {
      setStatus("Select an item first", false);
      return;
    }

    if (!selectedItem.isChecked()) {
      setStatus("Mark the item as checked before moving it to the pantry", false);
      return;
    }

    Ingredient ingredient = new Ingredient(
            selectedItem.getName(),
            selectedItem.getQuantity(),
            selectedItem.getUnit(),
            ""
    );

    ingredient.setUserID(Session.getInstance().getUid());

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.addIngredient(ingredient);
        firebaseService.deleteShoppingItem(selectedItem.getId());
        return null;
      }
    };

    task.setOnSucceeded(event -> {
      setStatus("Moved \"" + selectedItem.getName() + "\" to pantry", true);
      onRefresh();
    });

    task.setOnFailed(event -> {
      setStatus("Move failed: " + task.getException().getMessage(), false);
    });

    Thread thread = new Thread(task, "shopping-to-pantry");
    thread.setDaemon(true);
    thread.start();
  }

  @FXML
  private void onDelete() {
    ShoppingItem selectedItem = listView.getSelectionModel().getSelectedItem();

    if (selectedItem == null || selectedItem.getId() == null) {
      setStatus("Select an item to delete", false);
      return;
    }

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.deleteShoppingItem(selectedItem.getId());
        return null;
      }
    };

    task.setOnSucceeded(event -> onRefresh());

    task.setOnFailed(event -> {
      setStatus("Delete failed: " + task.getException().getMessage(), false);
    });

    Thread thread = new Thread(task, "shopping-delete");
    thread.setDaemon(true);
    thread.start();
  }

  private void updateItemCount() {
    int checkedCount = 0;

    for (ShoppingItem item : items) {
      if (item.isChecked()) {
        checkedCount++;
      }
    }

    setStatus(checkedCount + " of " + items.size() + " items checked", true);
  }

  private static class ShoppingCell extends ListCell<ShoppingItem> {
    @Override
    protected void updateItem(ShoppingItem item, boolean empty) {
      super.updateItem(item, empty);

      if (empty || item == null) {
        setText(null);
        setStyle("");
        return;
      }

      String checkSymbol;

      if (item.isChecked()) {
        checkSymbol = "✓";
      } else {
        checkSymbol = "○";
      }

      String itemText = checkSymbol + "  " + item.getName();
      itemText += "  —  " + formatQuantity(item.getQuantity());

      if (item.getUnit() != null && !item.getUnit().isBlank()) {
        itemText += " " + item.getUnit();
      }

      setText(itemText);

      if (item.isChecked()) {
        setStyle(
                "-fx-text-fill: #999999;"
                        + "-fx-font-style: italic;"
                        + "-fx-font-size: 14px;"
                        + "-fx-padding: 12;"
        );
      } else {
        setStyle(
                "-fx-text-fill: #222222;"
                        + "-fx-font-size: 14px;"
                        + "-fx-padding: 12;"
        );
      }
    }

    private static String formatQuantity(double quantity) {
      if (quantity == Math.floor(quantity)) {
        return String.valueOf((int) quantity);
      }

      return String.valueOf(quantity);
    }
  }

  // inline nav bar
  @FXML
  private void onNavPantry() {
    navigateTo(Nav.Screen.PANTRY);
  }

  @FXML
  private void onNavAdd() {
    navigateTo(Nav.Screen.ADD);
  }

  @FXML
  private void onNavRecipes() {
    navigateTo(Nav.Screen.RECIPES);
  }

  @FXML
  private void onNavShopping() {
    /* already here */
  }

  @FXML
  private void onNavProfile() {
    navigateTo(Nav.Screen.PROFILE);
  }

  private void navigateTo(Nav.Screen screen) {
    try {
      Stage stage = (Stage) listView.getScene().getWindow();
      Nav.go(stage, screen);
    } catch (Exception exception) {
      setStatus("Navigation error: " + exception.getMessage(), false);
    }
  }

  private void setStatus(String message, boolean success) {
    Platform.runLater(() -> {
      statusLabel.setText(message);

      if (success) {
        statusLabel.setStyle(
                "-fx-font-size: 12px;"
                        + "-fx-font-style: italic;"
                        + "-fx-text-fill: #D7E5DD;"
        );
      } else {
        statusLabel.setStyle(
                "-fx-font-size: 12px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #FFD1D1;"
        );
      }
    });
  }
}
