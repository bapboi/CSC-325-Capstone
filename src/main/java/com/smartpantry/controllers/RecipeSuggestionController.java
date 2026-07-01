
package com.smartpantry.controllers;

import com.smartpantry.model.Ingredient;
import com.smartpantry.services.FirebaseService;
import com.smartpantry.services.GeminiRecipeService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeSuggestionController {

  @FXML
  private Button findButton;
  @FXML
  private ProgressIndicator progressIndicator;
  @FXML
  private Label statusLabel;
  @FXML
  private Label resultLabel;
  @FXML
  private Hyperlink resultLink;

  private final FirebaseService firebaseService = FirebaseService.getInstance();
  private final GeminiRecipeService geminiRecipeService = new GeminiRecipeService();

  @FXML
  public void initialize() {
    progressIndicator.setVisible(false);
    resultLink.setVisible(false);
  }

  @FXML
  private void onBackToPantry() {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/smartpantry/fxml/AddIngredient.fxml"));
      Parent root = loader.load();
      Stage stage = (Stage) findButton.getScene().getWindow();
      stage.setScene(new Scene(root, 420, 760));
    } catch (IOException e) {
      setStatus("Failed to go back: " + e.getMessage(), false);
    }
  }

  @FXML
  private void onFindRecipe() {
    if (!firebaseService.isConnected()) {
      setStatus("Connect Firebase on the Pantry screen first", false);
      return;
    }

    findButton.setDisable(true);
    progressIndicator.setVisible(true);
    resultLink.setVisible(false);
    resultLabel.setText("");
    setStatus("Searching based on your pantry items...", true);

    Task<GeminiRecipeService.RecipeResult> task = new Task<>() {
      @Override
      protected GeminiRecipeService.RecipeResult call() throws Exception {
        List<Ingredient> pantry = firebaseService.getAllIngredients();
        if (pantry.isEmpty()) {
          throw new IllegalStateException("Your pantry is empty - add some ingredients first.");
        }
        List<String> names = pantry.stream().map(Ingredient::getName).collect(Collectors.toList());
        return geminiRecipeService.findRecipe(names);
      }
    };

    task.setOnSucceeded(e -> {
      GeminiRecipeService.RecipeResult result = task.getValue();
      resultLabel.setText(result.description());
      if (result.link() != null) {
        resultLink.setText(result.link());
        resultLink.setVisible(true);
      }
      setStatus("Done", true);
      progressIndicator.setVisible(false);
      findButton.setDisable(false);
    });

    task.setOnFailed(e -> {
      setStatus(task.getException().getMessage(), false);
      progressIndicator.setVisible(false);
      findButton.setDisable(false);
    });

    new Thread(task, "gemini-recipe-search").start();
  }

  @FXML
  private void onOpenLink() {
    String url = resultLink.getText();
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(new URI(url));
      }
    } catch (Exception ex) {
      setStatus("Could not open link: " + ex.getMessage(), false);
    }
  }

  private void setStatus(String message, boolean ok) {
    Platform.runLater(() -> {
      statusLabel.setText(message);
      statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    });
  }
}
