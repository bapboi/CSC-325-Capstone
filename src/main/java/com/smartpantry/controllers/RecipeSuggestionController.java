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
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
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

  // Kept so "load more" can tell Gemini what's already shown
  private List<String> currentPantryIngredients = List.of();

  @FXML
  public void initialize() {
    progressIndicator.setVisible(false);
    loadMoreButton.setVisible(false);
    recipeListView.setItems(recipes);
    recipeListView.setCellFactory(lv -> new RecipeCard(this::onFavoriteRecipe, this::onAddMissingToShopping));

    recipeListView.setOnMouseClicked(event -> {
      if (event.getClickCount() == 1) {
        Recipe selected = recipeListView.getSelectionModel().getSelectedItem();
        if (selected != null)
          openRecipeDetails(selected);
      }
    });
  }

  // ── Find recipes ──────────────────────────────────────────────────────────

  @FXML
  private void onFindRecipe() {
    if (!firebaseService.isConnected()) {
      setStatus("Not connected to Firebase.", false);
      return;
    }

    findButton.setDisable(true);
    loadMoreButton.setVisible(false);
    progressIndicator.setVisible(true);
    recipes.clear();
    cacheLabel.setText("");
    setStatus("Reading your pantry...", true);

    Task<List<Recipe>> task = new Task<>() {
      @Override
      protected List<Recipe> call() throws Exception {
        List<Ingredient> pantry = firebaseService.getAllIngredients();
        if (pantry.isEmpty())
          throw new IllegalStateException("Your pantry is empty — add some ingredients first.");

        currentPantryIngredients = pantry.stream()
            .map(Ingredient::getName)
            .collect(Collectors.toList());

        String cacheKey = RecipeCache.keyFor(currentPantryIngredients);
        boolean fromCache = RecipeCache.get(cacheKey) != null;
        if (fromCache)
          Platform.runLater(() -> cacheLabel.setText("Loaded from cache"));

        return geminiService.findRecipesByPantry(currentPantryIngredients);
      }
    };

    task.setOnSucceeded(e -> {
      recipes.setAll(task.getValue());
      setStatus(recipes.size() + " recipe(s) found — tap one to view details", true);
      progressIndicator.setVisible(false);
      loadMoreButton.setVisible(true);
      findButton.setDisable(false);
    });

    task.setOnFailed(e -> {
      String message = task.getException() == null
          ? "Unknown error"
          : task.getException().getMessage();
      setStatus(message, false);
      progressIndicator.setVisible(false);
      findButton.setDisable(false);
    });

    new Thread(task, "gemini-recipe-search").start();
  }

  // ── Load more ─────────────────────────────────────────────────────────────

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
        return geminiService.findMoreRecipes(currentPantryIngredients, alreadyShown);
      }
    };
    task.setOnSucceeded(e -> {
      recipes.addAll(task.getValue());
      setStatus(recipes.size() + " recipe(s) total", true);
      progressIndicator.setVisible(false);
      loadMoreButton.setDisable(false);
    });
    task.setOnFailed(e -> {
      setStatus("Load more failed: " + task.getException().getMessage(), false);
      progressIndicator.setVisible(false);
      loadMoreButton.setDisable(false);
    });
    new Thread(task, "gemini-load-more").start();
  }

  @FXML
  private void onClearCache() {
    RecipeCache.clear();
    cacheLabel.setText("Cache cleared");
    setStatus("Cache cleared — next search will call Gemini fresh.", true);
  }

  // ── Card actions ──────────────────────────────────────────────────────────

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
    task.setOnSucceeded(e -> setStatus("\"" + recipe.getName() + "\" saved to favourites.", true));
    task.setOnFailed(e -> setStatus("Failed to save: " + task.getException().getMessage(), false));
    new Thread(task, "save-favourite").start();
  }

  private void onAddMissingToShopping(Recipe recipe) {
    if (recipe.getMissingIngredients() == null || recipe.getMissingIngredients().isEmpty()) {
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
        String uid = Session.getInstance().getUid();
        for (String name : recipe.getMissingIngredients()) {
          firebaseService.addShoppingItem(new ShoppingItem(name, 1.0, "", uid));
        }
        return null;
      }
    };
    task.setOnSucceeded(e -> setStatus(
        recipe.getMissingIngredients().size() + " missing item(s) added to shopping list.", true));
    task.setOnFailed(e -> setStatus("Failed: " + task.getException().getMessage(), false));
    new Thread(task, "add-missing-shopping").start();
  }

  private void openRecipeDetails(Recipe recipe) {
    SelectedRecipeStore.setSelectedRecipe(recipe);
    try {
      Nav.go((Stage) recipeListView.getScene().getWindow(), Nav.Screen.RECIPE_DETAILS);
    } catch (IOException e) {
      setStatus("Failed to open recipe: " + e.getMessage(), false);
    }
  }

  // ── Recipe card cell ──────────────────────────────────────────────────────

  private static class RecipeCard extends ListCell<Recipe> {

    private final ImageView thumbnail = new ImageView();
    private final Label nameLabel = new Label();
    private final Label metaLabel = new Label();
    private final Label descLabel = new Label();
    private final Label matchLabel = new Label();
    private final ProgressBar matchBar = new ProgressBar(0);
    private final Button favouriteBtn = new Button("♡ Save");
    private final Button missingBtn = new Button("+ Missing → List");
    private final VBox card;

    private final Consumer<Recipe> onFavourite;
    private final Consumer<Recipe> onAddMissing;

    RecipeCard(Consumer<Recipe> onFavourite, Consumer<Recipe> onAddMissing) {
      this.onFavourite = onFavourite;
      this.onAddMissing = onAddMissing;

      thumbnail.setFitWidth(80);
      thumbnail.setFitHeight(80);
      thumbnail.setPreserveRatio(true);

      nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
      nameLabel.setWrapText(true);
      metaLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
      descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #444;");
      descLabel.setWrapText(true);
      matchLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #2f6b4f; -fx-font-weight: bold;");
      matchBar.setPrefWidth(160);
      matchBar.setStyle("-fx-accent: #2f6b4f;");

      favouriteBtn.setStyle("-fx-font-size: 10px;");
      missingBtn.setStyle("-fx-font-size: 10px; -fx-text-fill: #c62828;");

      favouriteBtn.setOnAction(e -> {
        if (getItem() != null)
          onFavourite.accept(getItem());
        e.consume();
      });
      missingBtn.setOnAction(e -> {
        if (getItem() != null)
          onAddMissing.accept(getItem());
        e.consume();
      });

      HBox matchRow = new HBox(8, matchBar, matchLabel);
      matchRow.setAlignment(Pos.CENTER_LEFT);
      HBox actionRow = new HBox(8, favouriteBtn, missingBtn);

      VBox textCol = new VBox(4, nameLabel, metaLabel, descLabel, matchRow, actionRow);
      HBox.setHgrow(textCol, Priority.ALWAYS);

      HBox row = new HBox(12, thumbnail, textCol);
      row.setAlignment(Pos.CENTER_LEFT);

      card = new VBox(row);
      card.setStyle(
          "-fx-background-color: white;"
              + "-fx-border-color: #dde8e0;"
              + "-fx-border-radius: 10;"
              + "-fx-background-radius: 10;"
              + "-fx-padding: 12;");
      setStyle("-fx-padding: 5 10 5 10; -fx-cursor: hand;");
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
      descLabel.setText(item.getDescription() != null ? item.getDescription() : "");

      int pct = item.getPantryMatchPercent();
      matchBar.setProgress(pct / 100.0);
      matchLabel.setText(pct + "% pantry match");

      boolean hasMissing = item.getMissingIngredients() != null
          && !item.getMissingIngredients().isEmpty();
      missingBtn.setVisible(hasMissing);
      missingBtn.setManaged(hasMissing);
      if (hasMissing)
        missingBtn.setText("+ " + item.getMissingIngredients().size() + " missing → List");

      // Thumbnail: try stored imageUrl, fall back to og:image fetch
      thumbnail.setImage(null);
      final Recipe currentItem = item;
      String storedUrl = item.getImageUrl();

      if (storedUrl != null && !storedUrl.isBlank()) {
        tryLoadImage(storedUrl, item.getSourceLink(), currentItem);
      } else {
        fetchOgImage(item.getSourceLink(), currentItem);
      }

      setGraphic(card);
    }

    private void tryLoadImage(String url, String fallbackPage, Recipe currentItem) {
      try {
        Image img = new Image(url, 80, 80, true, true, true);
        img.errorProperty().addListener((obs, old, err) -> {
          if (err)
            fetchOgImage(fallbackPage, currentItem);
        });
        thumbnail.setImage(img);
      } catch (Exception e) {
        fetchOgImage(fallbackPage, currentItem);
      }
    }

    private void fetchOgImage(String pageUrl, Recipe currentItem) {
      if (pageUrl == null || pageUrl.isBlank())
        return;
      Thread t = new Thread(() -> {
        String ogUrl = OgImageFetcher.fetch(pageUrl);
        if (ogUrl == null)
          return;
        Platform.runLater(() -> {
          // Guard against cell recycling
          if (getItem() != currentItem)
            return;
          try {
            Image img = new Image(ogUrl, 80, 80, true, true, true);
            thumbnail.setImage(img);
          } catch (Exception ignored) {
          }
        });
      }, "og-thumb-fetch");
      t.setDaemon(true);
      t.start();
    }

    private String buildMeta(Recipe r) {
      StringBuilder sb = new StringBuilder();
      if (r.getPrepTime() != null && !r.getPrepTime().isBlank())
        sb.append("Prep ").append(r.getPrepTime()).append("  ·  ");
      if (r.getCookTime() != null && !r.getCookTime().isBlank())
        sb.append("Cook ").append(r.getCookTime()).append("  ·  ");
      if (r.getDifficulty() != null && !r.getDifficulty().isBlank())
        sb.append(r.getDifficulty()).append("  ·  ");
      if (r.getServings() > 0)
        sb.append(r.getServings()).append(" servings");
      String s = sb.toString();
      return s.endsWith("  ·  ") ? s.substring(0, s.length() - 5) : s;
    }
  }

  // ── Nav (HEAD style — full bar) ───────────────────────────────────────────

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
    /* already here */ }

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
      Nav.go((Stage) findButton.getScene().getWindow(), screen);
    } catch (Exception e) {
      setStatus("Navigation error: " + e.getMessage(), false);
    }
  }

  private void setStatus(String message, boolean ok) {
    Platform.runLater(() -> {
      statusLabel.setText(message);
      statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    });
  }
}
