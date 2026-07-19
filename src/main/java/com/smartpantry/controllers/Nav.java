package com.smartpantry.controllers;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Nav {

    public enum Screen {
        LOGIN          ("/com/smartpantry/fxml/Login.fxml",           420, 760),
        PANTRY         ("/com/smartpantry/fxml/PantryDashboard.fxml", 420, 760),
        ADD            ("/com/smartpantry/fxml/AddIngredient.fxml",   420, 760),
        RECIPES        ("/com/smartpantry/fxml/RecipeSuggestion.fxml",420, 760),
        RECIPE_DETAILS ("/com/smartpantry/fxml/RecipeDetails.fxml",   420, 760),
        SAVED_RECIPES  ("/com/smartpantry/fxml/SavedRecipes.fxml",    420, 760),
        SHOPPING       ("/com/smartpantry/fxml/ShoppingList.fxml",    420, 760),
        PROFILE        ("/com/smartpantry/fxml/Profile.fxml",         420, 760),
        AI_DETECTION   ("/com/smartpantry/fxml/AIDetection.fxml",     420, 760),
        SETTINGS       ("/com/smartpantry/fxml/settings.fxml",        420, 760);

        final String fxml;
        final int w;
        final int h;

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
