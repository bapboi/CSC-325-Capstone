package com.smartpantry.controller;

import com.smartpantry.model.Ingredient;
import com.smartpantry.service.FirebaseService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;

public class MainController {

    @FXML private TableView<Ingredient> pantryTable;
    @FXML private TableColumn<Ingredient, String> nameColumn;
    @FXML private TableColumn<Ingredient, Double> quantityColumn;
    @FXML private TableColumn<Ingredient, String> unitColumn;
    @FXML private TableColumn<Ingredient, String> categoryColumn;
    @FXML private TableColumn<Ingredient, String> addedDateColumn;

    @FXML private TextField nameField;
    @FXML private TextField quantityField;
    @FXML private TextField unitField;
    @FXML private TextField categoryField;

    @FXML private Label statusLabel;

    private final ObservableList<Ingredient> ingredients = FXCollections.observableArrayList();
    private final FirebaseService firebaseService = FirebaseService.getInstance();

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("unit"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        addedDateColumn.setCellValueFactory(new PropertyValueFactory<>("addedDate"));
        pantryTable.setItems(ingredients);

        autoConnectIfPossible();
    }

    private void autoConnectIfPossible() {
        String path = firebaseService.resolveSavedCredentialPath();
        if (path == null) {
            setStatus("Not connected - click \"Connect Firebase\"", false);
            return;
        }
        connect(path);
    }

    @FXML
    private void onChooseCredentialFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select your Firebase service account JSON key");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        File file = chooser.showOpenDialog(pantryTable.getScene().getWindow());
        if (file != null) {
            connect(file.getAbsolutePath());
        }
    }

    private void connect(String credentialPath) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                firebaseService.initialize(credentialPath);
                firebaseService.saveCredentialPath(credentialPath);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setStatus("Connected to Firebase", true);
            onRefresh();
        });
        task.setOnFailed(e -> setStatus("Connection failed: " + task.getException().getMessage(), false));
        new Thread(task, "firebase-connect").start();
    }

    @FXML
    private void onRefresh() {
        if (!firebaseService.isConnected()) {
            setStatus("Not connected to Firebase yet", false);
            return;
        }
        Task<List<Ingredient>> task = new Task<>() {
            @Override
            protected List<Ingredient> call() throws Exception {
                return firebaseService.getAllIngredients();
            }
        };
        task.setOnSucceeded(e -> {
            ingredients.setAll(task.getValue());
            setStatus("Connected to Firebase", true);
        });
        task.setOnFailed(e -> setStatus("Failed to load pantry: " + task.getException().getMessage(), false));
        new Thread(task, "firebase-refresh").start();
    }

    @FXML
    private void onAddIngredient() {
        String name = nameField.getText();
        if (name == null || name.isBlank()) {
            setStatus("Enter an ingredient name first", false);
            return;
        }
        double qty;
        try {
            qty = quantityField.getText() == null || quantityField.getText().isBlank()
                    ? 1.0
                    : Double.parseDouble(quantityField.getText());
        } catch (NumberFormatException ex) {
            setStatus("Quantity must be a number", false);
            return;
        }

        Ingredient ingredient = new Ingredient(name.trim(), qty, unitField.getText(), categoryField.getText());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                firebaseService.addIngredient(ingredient);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            nameField.clear();
            quantityField.clear();
            unitField.clear();
            categoryField.clear();
            onRefresh();
        });
        task.setOnFailed(e -> setStatus("Failed to add ingredient: " + task.getException().getMessage(), false));
        new Thread(task, "firebase-add").start();
    }

    @FXML
    private void onDeleteSelected() {
        Ingredient selected = pantryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select an ingredient to delete first", false);
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                firebaseService.deleteIngredient(selected.getId());
                return null;
            }
        };
        task.setOnSucceeded(e -> onRefresh());
        task.setOnFailed(e -> setStatus("Failed to delete: " + task.getException().getMessage(), false));
        new Thread(task, "firebase-delete").start();
    }

    private void setStatus(String message, boolean ok) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle(ok ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
        });
    }
}
