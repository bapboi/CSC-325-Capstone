package com.smartpantry.controllers;

import com.google.cloud.Timestamp;
import com.smartpantry.model.Ingredient;
import com.smartpantry.services.FirebaseService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class PantryDashboardController {

  @FXML
  private Label ingredientCountLabel;

  @FXML
  private Label statusLabel;

  @FXML
  private TextField searchField;

  @FXML
  private TilePane pantryTilePane;

  private final FirebaseService firebaseService =
          FirebaseService.getInstance();

  private final ObservableList<Ingredient> items =
          FXCollections.observableArrayList();

  private Ingredient selectedIngredient;
  private VBox selectedCard;

  @FXML
  public void initialize() {
    searchField.textProperty().addListener(
            (observable, oldValue, newValue) ->
                    onSearchChanged(newValue)
    );

    if (firebaseService.isConnected()) {
      onRefresh();
    } else {
      // checks if firebase is live
      setStatus("Connecting...", true);

      Thread waitThread = new Thread(() -> {
        for (int i = 0; i < 20; i++) {
          try {
            Thread.sleep(200);
          } catch (InterruptedException ignored) {
          }

          if (firebaseService.isConnected()) {
            break;
          }
        }

        Platform.runLater(() -> {
          if (firebaseService.isConnected()) {
            onRefresh();
          } else {
            setStatus(
                    "Unable to connect to Firebase.",
                    false
            );
          }
        });
      }, "pantry-wait-firebase");

      waitThread.setDaemon(true);
      waitThread.start();
    }
  }

  @FXML
  private void onRefresh() {
    if (!firebaseService.isConnected()) {
      setStatus(
              "Unable to connect to Firebase.",
              false
      );

      return;
    }

    setStatus("Loading pantry...", true);

    Task<List<Ingredient>> task = new Task<>() {
      @Override
      protected List<Ingredient> call() throws Exception {
        return firebaseService.getAllIngredients();
      }
    };

    task.setOnSucceeded(event -> {
      List<Ingredient> loadedItems = task.getValue();

      if (loadedItems == null) {
        items.clear();
      } else {
        items.setAll(loadedItems);
      }

      clearSelection();
      updateIngredientCount();

      int matchCount =
              displayIngredients(
                      searchField.getText()
              );

      String searchText =
              searchField.getText();

      if (items.isEmpty()) {
        setStatus(
                "Your pantry is empty. Add an ingredient to get started.",
                true
        );
      } else if (searchText != null
              && !searchText.isBlank()
              && matchCount == 0) {

        setStatus(
                "No ingredients found for \""
                        + searchText.trim()
                        + "\".",
                false
        );
      } else {
        setStatus("", true);
      }
    });

    task.setOnFailed(event ->
            setStatus(
                    "Unable to load pantry: "
                            + getErrorMessage(
                            task.getException()
                    ),
                    false
            )
    );

    startTask(task, "pantry-load");
  }

  private void onSearchChanged(
          String searchText
  ) {
    clearSelection();

    int matchCount =
            displayIngredients(searchText);

    if (searchText == null
            || searchText.isBlank()) {

      setStatus("", true);
    } else if (matchCount == 0) {
      setStatus(
              "No ingredients found for \""
                      + searchText.trim()
                      + "\".",
              false
      );
    } else if (matchCount == 1) {
      setStatus(
              "1 ingredient found",
              true
      );
    } else {
      setStatus(
              matchCount + " ingredients found",
              true
      );
    }
  }

  private int displayIngredients(
          String searchText
  ) {
    pantryTilePane.getChildren().clear();

    String searchValue = "";

    if (searchText != null) {
      searchValue =
              searchText.trim().toLowerCase();
    }

    int matchCount = 0;

    for (Ingredient ingredient : items) {
      if (matchesSearch(
              ingredient,
              searchValue
      )) {
        pantryTilePane.getChildren().add(
                createIngredientCard(ingredient)
        );

        matchCount++;
      }
    }

    return matchCount;
  }

  private boolean matchesSearch(
          Ingredient ingredient,
          String searchValue
  ) {
    if (searchValue.isBlank()) {
      return true;
    }

    String name =
            nullSafe(
                    ingredient.getName()
            ).toLowerCase();

    String category =
            nullSafe(
                    ingredient.getCategory()
            ).toLowerCase();

    String unit =
            nullSafe(
                    ingredient.getUnit()
            ).toLowerCase();

    return name.contains(searchValue)
            || category.contains(searchValue)
            || unit.contains(searchValue);
  }

  private VBox createIngredientCard(
          Ingredient ingredient
  ) {
    Label nameLabel =
            new Label(
                    getIngredientName(ingredient)
            );

    nameLabel.setWrapText(true);
    nameLabel.setMaxWidth(85);
    nameLabel.setAlignment(
            javafx.geometry.Pos.CENTER
    );

    nameLabel.setStyle(
            "-fx-font-size: 12px;"
                    + "-fx-font-weight: bold;"
                    + "-fx-text-fill: #202823;"
    );

    Label amountLabel =
            new Label(
                    getAmountText(ingredient)
            );

    amountLabel.setWrapText(true);
    amountLabel.setMaxWidth(85);
    amountLabel.setAlignment(
            javafx.geometry.Pos.CENTER
    );

    amountLabel.setStyle(
            "-fx-font-size: 10px;"
                    + "-fx-text-fill: #555E59;"
    );

    Label expirationLabel =
            new Label(
                    getExpirationText(ingredient)
            );

    expirationLabel.setWrapText(true);
    expirationLabel.setMaxWidth(85);
    expirationLabel.setAlignment(
            javafx.geometry.Pos.CENTER
    );

    expirationLabel.setStyle(
            getExpirationStyle(ingredient)
    );

    VBox card = new VBox(
            4,
            nameLabel,
            amountLabel,
            expirationLabel
    );

    card.setAlignment(
            javafx.geometry.Pos.CENTER
    );

    card.setPrefWidth(94);
    card.setPrefHeight(78);
    card.setMinWidth(94);
    card.setMinHeight(78);
    card.setMaxWidth(94);
    card.setMaxHeight(78);

    card.setStyle(
            getNormalCardStyle(ingredient)
    );

    card.setOnMouseClicked(event ->
            selectIngredient(
                    ingredient,
                    card
            )
    );

    return card;
  }

  private void selectIngredient(
          Ingredient ingredient,
          VBox card
  ) {
    clearSelection();

    selectedIngredient = ingredient;
    selectedCard = card;

    card.setStyle(
            "-fx-background-color: white;"
                    + "-fx-background-radius: 15;"
                    + "-fx-border-color: #F29A32;"
                    + "-fx-border-width: 2;"
                    + "-fx-border-radius: 15;"
                    + "-fx-cursor: hand;"
    );

    setStatus(
            ingredient.getName()
                    + " selected",
            true
    );
  }

  private void clearSelection() {
    if (selectedCard != null
            && selectedIngredient != null) {

      selectedCard.setStyle(
              getNormalCardStyle(
                      selectedIngredient
              )
      );
    }

    selectedIngredient = null;
    selectedCard = null;
  }

  @FXML
  private void onDelete() {
    if (selectedIngredient == null) {
      setStatus(
              "Select an ingredient before removing it.",
              false
      );

      return;
    }

    if (selectedIngredient.getId() == null
            || selectedIngredient.getId().isBlank()) {

      setStatus(
              "This ingredient could not be removed.",
              false
      );

      return;
    }

    Ingredient ingredientToDelete =
            selectedIngredient;

    setStatus(
            "Removing ingredient...",
            true
    );

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.deleteIngredient(
                ingredientToDelete.getId()
        );

        return null;
      }
    };

    task.setOnSucceeded(event -> {
      items.remove(ingredientToDelete);

      clearSelection();
      updateIngredientCount();

      int matchCount =
              displayIngredients(
                      searchField.getText()
              );

      String searchText =
              searchField.getText();

      if (searchText != null
              && !searchText.isBlank()
              && matchCount == 0) {

        setStatus(
                "No ingredients found for \""
                        + searchText.trim()
                        + "\".",
                false
        );
      } else {
        setStatus(
                ingredientToDelete.getName()
                        + " was removed from your pantry.",
                true
        );
      }
    });

    task.setOnFailed(event ->
            setStatus(
                    "Unable to remove ingredient: "
                            + getErrorMessage(
                            task.getException()
                    ),
                    false
            )
    );

    startTask(task, "pantry-delete");
  }

  private void updateIngredientCount() {
    int count = items.size();

    ingredientCountLabel.setText(
            "ALL INGREDIENTS (" + count + ")"
    );
  }

  private String getIngredientName(
          Ingredient ingredient
  ) {
    String name = ingredient.getName();

    if (name == null || name.isBlank()) {
      return "Unnamed";
    }

    return name;
  }

  private String getAmountText(
          Ingredient ingredient
  ) {
    double quantity =
            ingredient.getQuantity();

    String amount;

    if (quantity == Math.floor(quantity)) {
      amount =
              String.valueOf(
                      (int) quantity
              );
    } else {
      amount =
              String.valueOf(quantity);
    }

    String unit =
            nullSafe(
                    ingredient.getUnit()
            );

    if (!unit.isBlank()) {
      amount += " " + unit;
    }

    return amount;
  }

  private String getExpirationText(
          Ingredient ingredient
  ) {
    Timestamp timestamp =
            ingredient.getExpirationDate();

    if (timestamp == null) {
      return "No date";
    }

    LocalDate expirationDate =
            getExpirationDate(timestamp);

    long days = ChronoUnit.DAYS.between(
            LocalDate.now(),
            expirationDate
    );

    if (days < 0) {
      return "Expired";
    }

    if (days == 0) {
      return "Today";
    }

    if (days <= 3) {
      return "In " + days + " days";
    }

    DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern(
                    "MMM d"
            );

    return expirationDate.format(formatter);
  }

  private String getExpirationStyle(
          Ingredient ingredient
  ) {
    Timestamp timestamp =
            ingredient.getExpirationDate();

    if (timestamp == null) {
      return "-fx-font-size: 10px;"
              + "-fx-text-fill: #757B77;";
    }

    LocalDate expirationDate =
            getExpirationDate(timestamp);

    long days = ChronoUnit.DAYS.between(
            LocalDate.now(),
            expirationDate
    );

    if (days < 0) {
      return "-fx-font-size: 10px;"
              + "-fx-font-weight: bold;"
              + "-fx-text-fill: #B3261E;";
    }

    if (days <= 3) {
      return "-fx-font-size: 10px;"
              + "-fx-font-weight: bold;"
              + "-fx-text-fill: #D16A00;";
    }

    return "-fx-font-size: 10px;"
            + "-fx-text-fill: #343A36;";
  }

  private String getNormalCardStyle(
          Ingredient ingredient
  ) {
    String borderColor = "#D9DEDB";

    Timestamp timestamp =
            ingredient.getExpirationDate();

    if (timestamp != null) {
      LocalDate expirationDate =
              getExpirationDate(timestamp);

      long days = ChronoUnit.DAYS.between(
              LocalDate.now(),
              expirationDate
      );

      if (days <= 3) {
        borderColor = "#F29A32";
      }
    }

    return "-fx-background-color: white;"
            + "-fx-background-radius: 15;"
            + "-fx-border-color: "
            + borderColor
            + ";"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 15;"
            + "-fx-cursor: hand;";
  }

  private LocalDate getExpirationDate(
          Timestamp timestamp
  ) {
    return Instant.ofEpochSecond(
                    timestamp.getSeconds()
            )
            .atZone(
                    ZoneId.systemDefault()
            )
            .toLocalDate();
  }

  private void startTask(
          Task<?> task,
          String threadName
  ) {
    Thread thread =
            new Thread(task, threadName);

    thread.setDaemon(true);
    thread.start();
  }

  private String getErrorMessage(
          Throwable exception
  ) {
    if (exception == null
            || exception.getMessage() == null
            || exception.getMessage().isBlank()) {

      return "An unexpected error occurred.";
    }

    return exception.getMessage();
  }

  private String nullSafe(
          String value
  ) {
    if (value == null) {
      return "";
    }

    return value;
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

  private void nav(
          Nav.Screen screen
  ) {
    try {
      Stage stage =
              (Stage) pantryTilePane
                      .getScene()
                      .getWindow();

      Nav.go(stage, screen);
    } catch (Exception exception) {
      setStatus(
              "Navigation error: "
                      + getErrorMessage(
                      exception
              ),
              false
      );
    }
  }

  private void setStatus(
          String message,
          boolean ok
  ) {
    Platform.runLater(() -> {
      statusLabel.setText(message);

      if (ok) {
        statusLabel.setStyle(
                "-fx-font-size: 11px;"
                        + "-fx-text-fill: #4F6559;"
        );
      } else {
        statusLabel.setStyle(
                "-fx-font-size: 11px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #B3261E;"
        );
      }
    });
  }
}
