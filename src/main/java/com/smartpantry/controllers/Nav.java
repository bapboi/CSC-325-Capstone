package com.smartpantry.controllers;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Centralises all scene transitions. Every controller calls Nav.go(...) instead
 * of copying FXMLLoader boilerplate.
 */
public class Nav {

  public enum Screen {
    PANTRY("/com/smartpantry/fxml/PantryDashboard.fxml", 420, 760),
    ADD("/com/smartpantry/fxml/AddIngredient.fxml", 420, 760),
    RECIPES("/com/smartpantry/fxml/RecipeSuggestion.fxml", 420, 760),
    SHOPPING("/com/smartpantry/fxml/ShoppingList.fxml", 420, 760),
    PROFILE("/com/smartpantry/fxml/Profile.fxml", 420, 760);

    final String fxml;
    final int w, h;

    Screen(String fxml, int w, int h) {
      this.fxml = fxml;
      this.w = w;
      this.h = h;
    }
  }

  public static void go(Stage stage, Screen screen) throws IOException {
    FXMLLoader loader = new FXMLLoader(Nav.class.getResource(screen.fxml));
    Parent root = loader.load();
    stage.setScene(new Scene(root, screen.w, screen.h));
  }
}
