package com.smartpantry.controllers;

import com.google.cloud.Timestamp;
import com.smartpantry.model.Ingredient;
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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class PantryDashboardController {

  @FXML
  private Label userLabel;
  @FXML
  private Label statusLabel;
  @FXML
  private Label statsLabel;
  @FXML
  private ListView<Ingredient> pantryListView;

  private final FirebaseService firebaseService = FirebaseService.getInstance();
  private final ObservableList<Ingredient> items = FXCollections.observableArrayList();

  @FXML
  public void initialize() {
    userLabel.setText("Pantry — " + Session.getInstance().getEmail());
    pantryListView.setItems(items);
    pantryListView.setCellFactory(lv -> new IngredientCell());
    onRefresh();
  }

  @FXML
  private void onRefresh() {
    if (!firebaseService.isConnected()) {
      setStatus("Not connected to Firebase", false);
      return;
    }
    setStatus("Loading...", true);
    Task<List<Ingredient>> task = new Task<>() {
      @Override
      protected List<Ingredient> call() throws Exception {
        return firebaseService.getAllIngredients();
      }
    };
    task.setOnSucceeded(e -> {
      items.setAll(task.getValue());
      updateStats(task.getValue());
      setStatus(items.size() + " item(s)", true);
    });
    task.setOnFailed(e -> setStatus("Load failed: " + task.getException().getMessage(), false));
    new Thread(task, "pantry-load").start();
  }

  @FXML
  private void onDelete() {
    Ingredient selected = pantryListView.getSelectionModel().getSelectedItem();
    if (selected == null) {
      setStatus("Select an item to delete", false);
      return;
    }
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.deleteIngredient(selected.getId());
        return null;
      }
    };
    task.setOnSucceeded(e -> onRefresh());
    task.setOnFailed(e -> setStatus("Delete failed: " + task.getException().getMessage(), false));
    new Thread(task, "pantry-delete").start();
  }

  private void updateStats(List<Ingredient> ingredients) {
    long expired = 0, expiringSoon = 0;
    LocalDate today = LocalDate.now();
    for (Ingredient i : ingredients) {
      Timestamp ts = i.getExperationDate();
      if (ts == null)
        continue;
      LocalDate exp = Instant.ofEpochSecond(ts.getSeconds())
          .atZone(ZoneId.systemDefault()).toLocalDate();
      long days = ChronoUnit.DAYS.between(today, exp);
      if (days < 0)
        expired++;
      else if (days <= 3)
        expiringSoon++;
    }
    long expiredFinal = expired, soonFinal = expiringSoon;
    Platform.runLater(() -> {
      if (expiredFinal > 0 || soonFinal > 0) {
        statsLabel.setText("⚠ " + expiredFinal + " expired · " + soonFinal + " expiring within 3 days");
        statsLabel.setStyle("-fx-text-fill: #c62828;");
      } else {
        statsLabel.setText("All items fresh");
        statsLabel.setStyle("-fx-text-fill: #2e7d32;");
      }
    });
  }

  // ── Custom cell ───────────────────────────────────────────────────────────
  private static class IngredientCell extends ListCell<Ingredient> {
    private final Label nameLabel = new Label();
    private final Label detailLabel = new Label();
    private final Label expiryLabel = new Label();
    private final VBox box = new VBox(2, nameLabel, detailLabel, expiryLabel);

    IngredientCell() {
      nameLabel.setStyle("-fx-font-weight: bold;");
      detailLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
      expiryLabel.setStyle("-fx-font-size: 11px;");
    }

    @Override
    protected void updateItem(Ingredient item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        return;
      }

      nameLabel.setText(item.getName());
      detailLabel.setText(item.getQuantity() + " " + nullSafe(item.getUnit())
          + " · " + nullSafe(item.getCategory()));

      Timestamp ts = item.getExperationDate();
      if (ts != null) {
        LocalDate exp = Instant.ofEpochSecond(ts.getSeconds())
            .atZone(ZoneId.systemDefault()).toLocalDate();
        long days = ChronoUnit.DAYS.between(LocalDate.now(), exp);
        if (days < 0) {
          expiryLabel.setText("Expired " + Math.abs(days) + " day(s) ago");
          expiryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #c62828;");
        } else if (days <= 3) {
          expiryLabel.setText("Expires in " + days + " day(s)");
          expiryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #e65100;");
        } else {
          expiryLabel.setText("Expires " + exp);
          expiryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #2e7d32;");
        }
      } else {
        expiryLabel.setText("No expiry set");
        expiryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaa;");
      }
      setGraphic(box);
    }

    private String nullSafe(String s) {
      return s == null ? "" : s;
    }
  }

  // ── Nav ──────────────────────────────────────────────────────────────────
  @FXML
  private void onNavPantry() {
    /* already here */ }

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
    nav(Nav.Screen.SHOPPING);
  }

  @FXML
  private void onNavProfile() {
    nav(Nav.Screen.PROFILE);
  }

  private void nav(Nav.Screen screen) {
    try {
      Nav.go((Stage) pantryListView.getScene().getWindow(), screen);
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
