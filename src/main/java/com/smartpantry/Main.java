package com.smartpantry;

import com.smartpantry.services.FirebaseService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

  @Override
  public void start(Stage stage) throws Exception {
    // initializes firebase upon run
    String savedPath = FirebaseService.getInstance().resolveSavedCredentialPath();
    if (savedPath != null) {
      Thread initThread = new Thread(() -> {
        try {
          FirebaseService.getInstance().initialize(savedPath);
        } catch (Exception e) {
          System.err.println("Firebase init failed at startup: " + e.getMessage());
        }
      }, "firebase-startup");
      initThread.setDaemon(true);
      initThread.start();
    }

    FXMLLoader loader = new FXMLLoader(
        Main.class.getResource("/com/smartpantry/fxml/Login.fxml"));

    Scene scene = new Scene(loader.load(), 400, 700);
    stage.setTitle("SmartPantry");
    stage.setScene(scene);
    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
