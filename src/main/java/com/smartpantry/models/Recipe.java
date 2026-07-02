package com.smartpantry.models;

import java.util.List;

public class Recipe {

    private String recipeName;
    private List<Ingredient> ingredients;
    private String instructions;

    public Recipe() {
    }

    public Recipe(String recipeName, List<Ingredient> ingredients, String instructions) {
        this.recipeName = recipeName;
        this.ingredients = ingredients;
        this.instructions = instructions;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public void setRecipeName(String recipeName) {
        this.recipeName = recipeName;
    }

    public List<Ingredient> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<Ingredient> ingredients) {
        this.ingredients = ingredients;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }
}
