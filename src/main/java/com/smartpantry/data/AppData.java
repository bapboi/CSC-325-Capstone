package com.smartpantry.data;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class AppData {

    private AppData() {
    }

    public static String currentUserEmail = "demo@smartpantry.com";
    public static String selectedRecipe = "Veggie Omelet";

    public static final ObservableList<String> PANTRY_ITEMS = FXCollections.observableArrayList(
            "Eggs - 12 count",
            "Milk - 1 gallon",
            "Spinach - 1 bag",
            "Chicken Breast - 2 lb",
            "Rice - 1 box"
    );

    public static final ObservableList<String> RECIPES = FXCollections.observableArrayList(
            "Veggie Omelet",
            "Chicken Rice Bowl",
            "Spinach Smoothie",
            "Garlic Fried Rice"
    );

    public static final ObservableList<String> SAVED_RECIPES = FXCollections.observableArrayList(
            "Veggie Omelet",
            "Chicken Rice Bowl"
    );

    public static final ObservableList<String> SHOPPING_LIST = FXCollections.observableArrayList(
            "Tomatoes",
            "Onions",
            "Olive Oil"
    );
}
