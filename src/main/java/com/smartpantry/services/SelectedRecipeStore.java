package com.smartpantry.services;

import com.smartpantry.model.Recipe;

public final class SelectedRecipeStore {

    private static Recipe selectedRecipe;

    private SelectedRecipeStore() {
    }

    public static Recipe getSelectedRecipe() {
        return selectedRecipe;
    }

    public static void setSelectedRecipe(Recipe recipe) {
        selectedRecipe = recipe;
    }

    public static void clear() {
        selectedRecipe = null;
    }
}