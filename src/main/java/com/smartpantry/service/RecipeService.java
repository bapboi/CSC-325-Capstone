package com.smartpantry.service;

import com.smartpantry.model.Ingredient;
import com.smartpantry.model.Recipe;

import java.util.List;

/**
 * Matches recipes against the current pantry contents. Recipes could come
 * from a Firestore "recipes" collection, a public recipe API, or the
 * LlmService - this layer just ranks/filters whatever list you give it.
 */
public class RecipeService {

    public List<Recipe> findRecipesUsingPantry(List<Ingredient> pantryIngredients) {
        throw new UnsupportedOperationException(
                "TODO: query a recipe source and rank results by ingredient overlap with pantryIngredients");
    }
}
