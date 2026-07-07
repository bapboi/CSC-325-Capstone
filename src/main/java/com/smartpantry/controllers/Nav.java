package com.smartpantry.controllers;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

//wip navbar (currently all inlined)

public class Nav {

  public enum Screen {
    PANTRY("/com/smartpantry/fxml/pantry.fxml", 390, 844),
    ADD("/com/smartpantry/fxml/add-ingredient.fxml", 390, 844),
    RECIPES("/com/smartpantry/fxml/recipes.fxml", 390, 844),
    SHOPPING("/com/smartpantry/fxml/shopping-list.fxml", 390, 844),
    PROFILE("/com/smartpantry/fxml/settings.fxml", 390, 844);

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
