package com.smartpantry.service;

import com.smartpantry.model.Ingredient;
import com.smartpantry.model.Recipe;

import java.util.List;

/* 
 * placeholder 
 *
 * 
 */
public class LlmService {

  public List<Recipe> suggestRecipes(List<Ingredient> pantryIngredients) {
    throw new UnsupportedOperationException("TODO: prompt the LLM with the pantry contents");
  }

  public List<String> suggestSubstitutes(String ingredientName, String recipeContext) {
    throw new UnsupportedOperationException("TODO: prompt the LLM for substitutes given the recipe context");
  }

  public List<Recipe> suggestQuickRecipesForSurplus(Ingredient surplusIngredient) {
    throw new UnsupportedOperationException("TODO: prompt the LLM for quick recipes built around this ingredient");
  }
}
