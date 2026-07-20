package com.smartpantry.controllers;

import com.smartpantry.model.Ingredient;
import com.smartpantry.model.Recipe;
import com.smartpantry.model.ShoppingItem;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.GeminiRecipeService;
import com.smartpantry.services.OgImageFetcher;
import com.smartpantry.services.RecipeCache;
import com.smartpantry.services.SelectedRecipeStore;
import com.smartpantry.services.Session;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeSuggestionController {

  @FXML
  private Button findButton;
  @FXML
  private Button loadMoreButton;
  @FXML
  private Button clearCacheButton;
  @FXML
  private ProgressIndicator progressIndicator;
  @FXML
  private Label statusLabel;
  @FXML
  private Label cacheLabel;
  @FXML
  private ListView<Recipe> recipeListView;

  private final FirebaseService firebaseService = FirebaseService.getInstance();
  private final GeminiRecipeService geminiService = new GeminiRecipeService();
  private final ObservableList<Recipe> recipes = FXCollections.observableArrayList();

  private List<String> currentPantryIngredients = List.of();

  @FXML
  public void initialize() {
    progressIndicator.setVisible(false);
    loadMoreButton.setVisible(false);
    recipeListView.setItems(recipes);
    recipeListView.setCellFactory(lv -> new RecipeCard());

    recipeListView.setOnMouseClicked(event -> {
      if (event.getClickCount() == 1) {
        Recipe selected = recipeListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
          openRecipeDetails(selected);
        }
      }
    });

  }

  @FXML
  private void onFindRecipe() {
    if (!firebaseService.isConnected()) {
      setStatus("Not connected to Firebase.", false);
      return;
    }

    RecipeCache.clear();
    cacheLabel.setText("");

    findButton.setDisable(true);
    loadMoreButton.setVisible(false);
    progressIndicator.setVisible(true);
    recipes.clear();
    setStatus("Reading your pantry...", true);

    Task<List<Recipe>> task = new Task<>() {
      @Override
      protected List<Recipe> call() throws Exception {
        List<Ingredient> pantry = firebaseService.getAllIngredients();

        if (pantry.isEmpty()) {
          throw new IllegalStateException(
                  "Your pantry is empty — add some ingredients first.");
        }

        currentPantryIngredients = pantry.stream()
                .map(Ingredient::getName)
                .collect(Collectors.toList());

        String cacheKey = RecipeCache.keyFor(currentPantryIngredients);
        boolean fromCache = RecipeCache.get(cacheKey) != null;

        if (fromCache) {
          Platform.runLater(() -> cacheLabel.setText("Loaded from cache"));
        }

        return geminiService.findRecipesByPantry(currentPantryIngredients);
      }
    };

    task.setOnSucceeded(event -> {
      recipes.setAll(task.getValue());
      setStatus(
              recipes.size() + " recipe(s) found — tap one to view details",
              true);
      progressIndicator.setVisible(false);
      loadMoreButton.setVisible(true);
      findButton.setDisable(false);
    });

    task.setOnFailed(event -> {
      String message = task.getException() == null
              ? "Unknown error"
              : task.getException().getMessage();

      setStatus(message, false);
      progressIndicator.setVisible(false);
      findButton.setDisable(false);
    });

    new Thread(task, "gemini-recipe-search").start();
  }

  @FXML
  private void onLoadMore() {
    if (currentPantryIngredients.isEmpty()) {
      setStatus("Search first to load more.", false);
      return;
    }

    loadMoreButton.setDisable(true);
    progressIndicator.setVisible(true);
    cacheLabel.setText("");

    List<String> alreadyShown = recipes.stream()
            .map(Recipe::getName)
            .collect(Collectors.toList());

    Task<List<Recipe>> task = new Task<>() {
      @Override
      protected List<Recipe> call() throws Exception {
        return geminiService.findMoreRecipes(
                currentPantryIngredients,
                alreadyShown);
      }
    };

    task.setOnSucceeded(event -> {
      recipes.addAll(task.getValue());
      setStatus(recipes.size() + " recipe(s) total", true);
      progressIndicator.setVisible(false);
      loadMoreButton.setDisable(false);
    });

    task.setOnFailed(event -> {
      setStatus(
              "Load more failed: " + task.getException().getMessage(),
              false);
      progressIndicator.setVisible(false);
      loadMoreButton.setDisable(false);
    });

    new Thread(task, "gemini-load-more").start();
  }

  @FXML
  private void onClearCache() {
    RecipeCache.clear();
    cacheLabel.setText("Cache cleared");
    setStatus(
            "Cache cleared — next search will call Gemini fresh.",
            true);
  }

  private void onFavoriteRecipe(Recipe recipe) {
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

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        firebaseService.saveRecipe(recipe);
        return null;
      }
    };

    task.setOnSucceeded(event ->
            setStatus(
                    "\"" + recipe.getName() + "\" saved to favourites.",
                    true));

    task.setOnFailed(event ->
            setStatus(
                    "Failed to save: " + task.getException().getMessage(),
                    false));

    new Thread(task, "save-favourite").start();
  }

  private void onAddMissingToShopping(Recipe recipe) {
    if (recipe.getMissingIngredients() == null
            || recipe.getMissingIngredients().isEmpty()) {
      setStatus("No missing ingredients for this recipe.", true);
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

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        String currentUid = Session.getInstance().getUid();

        for (String name : recipe.getMissingIngredients()) {
          firebaseService.addShoppingItem(
                  new ShoppingItem(name, 1.0, "", currentUid));
        }

        return null;
      }
    };

    task.setOnSucceeded(event ->
            setStatus(
                    recipe.getMissingIngredients().size()
                            + " missing item(s) added to shopping list.",
                    true));

    task.setOnFailed(event ->
            setStatus(
                    "Failed: " + task.getException().getMessage(),
                    false));

    new Thread(task, "add-missing-shopping").start();
  }

  private void openRecipeDetails(Recipe recipe) {
    SelectedRecipeStore.setSelectedRecipe(recipe);

    try {
      Nav.go(
              (Stage) recipeListView.getScene().getWindow(),
              Nav.Screen.RECIPE_DETAILS);
    } catch (IOException exception) {
      setStatus(
              "Failed to open recipe: " + exception.getMessage(),
              false);
    }
  }

  private static class RecipeCard extends ListCell<Recipe> {

    private static final double IMAGE_WIDTH = 360;
    private static final double IMAGE_HEIGHT = 150;

    private final ImageView thumbnail = new ImageView();
    private final Label imagePlaceholder = new Label("Recipe Image");
    private final Label matchBadge = new Label();
    private final Label nameLabel = new Label();
    private final Label metaLabel = new Label();
    private final VBox card;

    RecipeCard() {
      thumbnail.setFitWidth(IMAGE_WIDTH);
      thumbnail.setFitHeight(IMAGE_HEIGHT);
      thumbnail.setPreserveRatio(false);
      thumbnail.setSmooth(true);
      thumbnail.setManaged(true);

      imagePlaceholder.setStyle(
              "-fx-text-fill: #7A8A82;"
                      + "-fx-font-size: 13px;"
                      + "-fx-font-weight: bold;");

      matchBadge.setStyle(
              "-fx-background-color: rgba(255,255,255,0.96);"
                      + "-fx-text-fill: #21634D;"
                      + "-fx-font-size: 11px;"
                      + "-fx-font-weight: bold;"
                      + "-fx-background-radius: 12;"
                      + "-fx-padding: 4 10;");

      StackPane imageStack = new StackPane(imagePlaceholder, thumbnail, matchBadge);
      imageStack.setPrefHeight(IMAGE_HEIGHT);
      imageStack.setMinHeight(IMAGE_HEIGHT);
      imageStack.setMaxHeight(IMAGE_HEIGHT);
      imageStack.setStyle(
              "-fx-background-color: #E8F0EA;"
                      + "-fx-background-radius: 18 18 0 0;");

      StackPane.setAlignment(matchBadge, Pos.TOP_LEFT);
      StackPane.setMargin(matchBadge, new Insets(10, 0, 0, 10));

      nameLabel.setStyle(
              "-fx-font-size: 16px;"
                      + "-fx-font-weight: bold;"
                      + "-fx-text-fill: #222222;");
      nameLabel.setWrapText(true);

      metaLabel.setStyle(
              "-fx-font-size: 12px;"
                      + "-fx-text-fill: #777777;");

      VBox textColumn = new VBox(4, nameLabel, metaLabel);
      textColumn.setPadding(new Insets(10, 14, 12, 14));
      VBox.setVgrow(textColumn, Priority.NEVER);

      card = new VBox(imageStack, textColumn);
      card.setSpacing(0);
      card.setPrefHeight(215);
      card.setMinHeight(215);
      card.setMaxHeight(215);
      card.setStyle(
              "-fx-background-color: white;"
                      + "-fx-background-radius: 18;"
                      + "-fx-border-color: #E6E6E6;"
                      + "-fx-border-radius: 18;"
                      + "-fx-border-width: 1;");

      setStyle(
              "-fx-background-color: transparent;"
                      + "-fx-padding: 8;"
                      + "-fx-cursor: hand;");
    }

    @Override
    protected void updateItem(Recipe item, boolean empty) {
      super.updateItem(item, empty);

      if (empty || item == null) {
        setGraphic(null);
        return;
      }

      nameLabel.setText(item.getName());
      metaLabel.setText(buildMeta(item));

      int percentage = item.getPantryMatchPercent();
      matchBadge.setText(percentage + "% match");

      thumbnail.setImage(null);

      Recipe currentItem = item;
      String storedUrl = item.getImageUrl();

      if (storedUrl != null && !storedUrl.isBlank()) {
        tryLoadImage(
                storedUrl,
                item.getSourceLink(),
                currentItem);
      } else {
        fetchOgImage(item.getSourceLink(), currentItem);
      }

      Rectangle clip =
              new Rectangle(IMAGE_WIDTH, IMAGE_HEIGHT);
      clip.setArcWidth(36);
      clip.setArcHeight(36);
      thumbnail.setClip(clip);

      setGraphic(card);
    }

    private void tryLoadImage(
            String url,
            String fallbackPage,
            Recipe currentItem) {

      try {
        Image image = new Image(
                url,
                IMAGE_WIDTH,
                IMAGE_HEIGHT,
                false,
                true,
                true);

        image.errorProperty().addListener(
                (observable, oldValue, hasError) -> {
                  if (hasError) {
                    fetchOgImage(fallbackPage, currentItem);
                  }
                });

        thumbnail.setImage(image);
      } catch (Exception exception) {
        fetchOgImage(fallbackPage, currentItem);
      }
    }

    private void fetchOgImage(
            String pageUrl,
            Recipe currentItem) {

      if (pageUrl == null || pageUrl.isBlank()) {
        return;
      }

      Thread thread = new Thread(() -> {
        String ogUrl = OgImageFetcher.fetch(pageUrl);

        if (ogUrl == null) {
          return;
        }

        Platform.runLater(() -> {
          if (getItem() != currentItem) {
            return;
          }

          try {
            Image image = new Image(
                    ogUrl,
                    IMAGE_WIDTH,
                    IMAGE_HEIGHT,
                    false,
                    true,
                    true);

            thumbnail.setImage(image);
          } catch (Exception ignored) {
            // Leave the image area blank when no image can be loaded.
          }
        });
      }, "og-thumb-fetch");

      thread.setDaemon(true);
      thread.start();
    }

    private String buildMeta(Recipe recipe) {
      StringBuilder result = new StringBuilder();

      if (recipe.getCookTime() != null
              && !recipe.getCookTime().isBlank()) {
        result.append("🕐 ")
                .append(recipe.getCookTime())
                .append("    ");
      }

      if (recipe.getDifficulty() != null
              && !recipe.getDifficulty().isBlank()) {
        result.append(recipe.getDifficulty());
      }

      return result.toString().trim();
    }
  }

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
    // Already on the Recipes screen.
  }

  @FXML
  private void onNavShopping() {
    nav(Nav.Screen.SHOPPING);
  }

  @FXML
  private void onNavProfile() {
    nav(Nav.Screen.PROFILE);
  }

  @FXML
  private void onNavSavedRecipes() {
    nav(Nav.Screen.SAVED_RECIPES);
  }

  private void nav(Nav.Screen screen) {
    try {
      Nav.go(
              (Stage) findButton.getScene().getWindow(),
              screen);
    } catch (Exception exception) {
      setStatus(
              "Navigation error: " + exception.getMessage(),
              false);
    }
  }

  private void setStatus(String message, boolean ok) {
    Platform.runLater(() -> {
      statusLabel.setText(message);
      statusLabel.setStyle(
              ok
                      ? "-fx-text-fill: #2e7d32;"
                      : "-fx-text-fill: #c62828;");
    });
  }
}
