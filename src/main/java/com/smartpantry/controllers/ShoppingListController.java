package com.smartpantry.controllers;

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
    listView.setCellFactory(lv -> new ShoppingCell());
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
    task.setOnSucceeded(e -> {
      items.setAll(task.getValue());
      long done = items.stream().filter(ShoppingItem::isChecked).count();
      setStatus(done + "/" + items.size() + " checked", true);
    });
    task.setOnFailed(e -> setStatus("Load failed: " + task.getException().getMessage(), false));
    new Thread(task, "shopping-load").start();
  }

  @FXML
  private void onAdd() {
    String name = nameField.getText();
    if (name == null || name.isBlank()) {
      setStatus("Enter an item name", false);
      return;
    }
    double qty;
    try {
      qty = quantityField.getText().isBlank() ? 1.0 : Double.parseDouble(quantityField.getText());
    } catch (NumberFormatException ex) {
      setStatus("Quantity must be a number", false);
      return;
    }

    ShoppingItem item = new ShoppingItem(name.trim(), qty, unitField.getText(), Session.getInstance().getUid());
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.addShoppingItem(item);
        return null;
      }
    };
    task.setOnSucceeded(e -> {
      nameField.clear();
      quantityField.clear();
      unitField.clear();
      onRefresh();
    });
    task.setOnFailed(e -> setStatus("Add failed: " + task.getException().getMessage(), false));
    new Thread(task, "shopping-add").start();
  }

  @FXML
  private void onToggleChecked() {
    ShoppingItem selected = listView.getSelectionModel().getSelectedItem();
    if (selected == null || selected.getId() == null) {
      setStatus("Select an item first", false);
      return;
    }
    boolean newState = !selected.isChecked();
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.updateShoppingItem(selected.getId(), Map.of("checked", newState));
        return null;
      }
    };
    task.setOnSucceeded(e -> onRefresh());
    task.setOnFailed(e -> setStatus("Update failed: " + task.getException().getMessage(), false));
    new Thread(task, "shopping-toggle").start();
  }

  /**
   * moves the selected checked item into pantryItems then removes it from the
   * shopping list
   */
  @FXML
  private void onMoveToPantry() {
    ShoppingItem selected = listView.getSelectionModel().getSelectedItem();
    if (selected == null || selected.getId() == null) {
      setStatus("Select an item first", false);
      return;
    }

    com.smartpantry.model.Ingredient ingredient = new com.smartpantry.model.Ingredient(
        selected.getName(), selected.getQuantity(), selected.getUnit(), "");
    ingredient.setUserID(Session.getInstance().getUid());

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.addIngredient(ingredient);
        firebaseService.deleteShoppingItem(selected.getId());
        return null;
      }
    };
    task.setOnSucceeded(e -> {
      setStatus("Moved \"" + selected.getName() + "\" to pantry", true);
      onRefresh();
    });
    task.setOnFailed(e -> setStatus("Failed: " + task.getException().getMessage(), false));
    new Thread(task, "shopping-to-pantry").start();
  }

  @FXML
  private void onDelete() {
    ShoppingItem selected = listView.getSelectionModel().getSelectedItem();
    if (selected == null || selected.getId() == null) {
      setStatus("Select an item to delete", false);
      return;
    }
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.deleteShoppingItem(selected.getId());
        return null;
      }
    };
    task.setOnSucceeded(e -> onRefresh());
    task.setOnFailed(e -> setStatus("Delete failed: " + task.getException().getMessage(), false));
    new Thread(task, "shopping-delete").start();
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
      setText((item.isChecked() ? "✓  " : "○  ") + item.getName()
          + "  —  " + item.getQuantity()
          + (item.getUnit() != null && !item.getUnit().isBlank() ? " " + item.getUnit() : ""));
      setStyle(item.isChecked()
          ? "-fx-text-fill: #aaa; -fx-font-style: italic;"
          : "-fx-text-fill: #222;");
    }
  }

  // inline nav bar
  @FXML
  private void onNavPantry() {
    nav(Nav.Screen.PANTRY);
  }

  @FXML
  private void onNavAdd() {
    nav(Nav.Screen.ADD);
  }

  @FXML
  private void onNavRecipes() {
    nav(Nav.Screen.RECIPES);
  }

  @FXML
  private void onNavShopping() {
    /* already here */ }

  @FXML
  private void onNavProfile() {
    nav(Nav.Screen.PROFILE);
  }

  private void nav(Nav.Screen screen) {
    try {
      Nav.go((Stage) listView.getScene().getWindow(), screen);
    } catch (Exception ex) {
      setStatus("Navigation error: " + ex.getMessage(), false);
    }
  }

  private void setStatus(String msg, boolean ok) {
    Platform.runLater(() -> {
      statusLabel.setText(msg);
      statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    });
  }
}
