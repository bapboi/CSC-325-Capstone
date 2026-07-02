package com.smartpantry.controllers;

import com.smartpantry.services.Session;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;

public class ProfileController {

  @FXML
  private Label emailLabel;
  @FXML
  private Label uidLabel;

  @FXML
  public void initialize() {
    Session session = Session.getInstance();
    emailLabel.setText(session.getEmail() != null ? session.getEmail() : "Unknown");
    uidLabel.setText(session.getUid() != null ? session.getUid() : "Unknown");
  }

  @FXML
  private void onSignOut() {
    Session.getInstance().clear();
    try {
      javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
          getClass().getResource("/com/smartpantry/fxml/Login.fxml"));
      javafx.scene.Parent root = loader.load();
      Stage stage = (Stage) emailLabel.getScene().getWindow();
      stage.setScene(new javafx.scene.Scene(root, 420, 760));
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  // ── Nav ──────────────────────────────────────────────────────────────────
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
    nav(Nav.Screen.SHOPPING);
  }

  @FXML
  private void onNavProfile() {
    /* already here */ }

  private void nav(Nav.Screen screen) {
    try {
      Nav.go((Stage) emailLabel.getScene().getWindow(), screen);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}
