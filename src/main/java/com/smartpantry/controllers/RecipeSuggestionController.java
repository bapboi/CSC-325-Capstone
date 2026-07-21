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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
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
        if (selected != null)
          openRecipeDetails(selected);
      }
    });
  }

  // Find recipes 

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

  //  Load more 

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

  //  Card actions (kept for future use on Recipe Details screen)

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

  //  Recipe card (matches Figma: image top, match badge, title, meta) ──

  private static class RecipeCard extends ListCell<Recipe> {

    // Loaded once from the bundled logo — shown immediately while the real
    // thumbnail is fetching, and kept permanently if fetching fails, so a
    // card never looks broken/blank.
    private static final Image PLACEHOLDER;
    static {
      Image ph = null;
      try {
        ph = new Image(
            RecipeCard.class.getResourceAsStream("/com/smartpantry/images/logo.png"),
            360, 160, false, true);
      } catch (Exception ignored) {
      }
      PLACEHOLDER = ph;
    }

    private final ImageView thumbnail = new ImageView();
    private final Label matchBadge = new Label();
    private final Label nameLabel = new Label();
    private final Label metaLabel = new Label();
    private final VBox card;

    RecipeCard() {
      thumbnail.setFitWidth(360);
      thumbnail.setFitHeight(160);
      thumbnail.setPreserveRatio(false);

      matchBadge.setStyle(
          "-fx-background-color: white;"
              + "-fx-text-fill: #2f6b4f;"
              + "-fx-font-size: 11px;"
              + "-fx-font-weight: bold;"
              + "-fx-background-radius: 12;"
              + "-fx-padding: 4 10;");

      StackPane imageStack = new StackPane(thumbnail, matchBadge);
      StackPane.setAlignment(matchBadge, Pos.TOP_LEFT);
      StackPane.setMargin(matchBadge, new Insets(10, 0, 0, 10));

      nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a1a1a;");
      nameLabel.setWrapText(true);
      metaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

      VBox textCol = new VBox(4, nameLabel, metaLabel);
      textCol.setStyle("-fx-padding: 12 14 14 14;");
      VBox.setVgrow(textCol, Priority.NEVER);

      card = new VBox(imageStack, textCol);
      card.setStyle(
          "-fx-background-color: white;"
              + "-fx-background-radius: 14;"
              + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);");
      setStyle("-fx-padding: 6 10 6 10; -fx-cursor: hand;");
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

      int pct = item.getPantryMatchPercent();
      matchBadge.setText(pct + "% match");

      // Show placeholder immediately so the card never looks broken; the
      // background task replaces it once the real image is ready.
      thumbnail.setImage(PLACEHOLDER);
      final Recipe currentItem = item;
      loadThumbnail(item.getImageUrl(), item.getSourceLink(), currentItem);

      Rectangle clip = new Rectangle(360, 160);
      clip.setArcWidth(28);
      clip.setArcHeight(28);
      thumbnail.setClip(clip);

      setGraphic(card);
    }

    /**
     * Resolves and downloads the thumbnail via OgImageFetcher, which:
     *  - scrapes the recipe page's real og:image tag first (more reliable
     *    than trusting Gemini's guessed imageUrl),
     *  - downloads with a proper Referer header (CDNs reject hotlinked
     *    requests without one),
     *  - transparently converts WebP/AVIF to JPEG via TwelveMonkeys, since
     *    JavaFX 21 cannot decode those formats natively — this was the
     *    actual cause of thumbnails "rarely" loading, since a large share
     *    of recipe sites serve WebP by default.
     */
    private void loadThumbnail(String geminiImageUrl, String sourceLink, Recipe currentItem) {
      Task<byte[]> task = new Task<>() {
        @Override
        protected byte[] call() {
          return OgImageFetcher.resolveThumbnailBytes(geminiImageUrl, sourceLink);
        }
      };
      task.setOnSucceeded(event -> {
        if (getItem() != currentItem) return; // cell was recycled
        byte[] bytes = task.getValue();
        if (bytes == null || bytes.length == 0) return;
        try {
          Image img = new Image(new java.io.ByteArrayInputStream(bytes), 360, 160, false, true);
          if (!img.isError()) {
            thumbnail.setImage(img);
          }
        } catch (Exception ignored) {
        }
      });
      Thread thread = new Thread(task, "recipe-thumb-fetch");
      thread.setDaemon(true);
      thread.start();
    }

    private String buildMeta(Recipe r) {
      StringBuilder sb = new StringBuilder();
      if (r.getCookTime() != null && !r.getCookTime().isBlank())
        sb.append("🕐 ").append(r.getCookTime()).append("   ");
      if (r.getDifficulty() != null && !r.getDifficulty().isBlank())
        sb.append(r.getDifficulty());
      return sb.toString().trim();
    }
  }

  // Nav

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

  @FXML
  private void onNavSavedRecipes() {
    nav(Nav.Screen.SAVED_RECIPES);
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
