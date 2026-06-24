package com.smartpantry.model;

import java.util.List;

// recipe base, merge and check with zeshan for his recipe skeleton since some of these fields i implemented weren't necessary tbh
public class Recipe {

  private String id;
  private String name;
  private List<String> ingredientNames;
  private String instructions;
  private int prepTimeMinutes;

  public Recipe() {
  }

  public Recipe(String name, List<String> ingredientNames, String instructions, int prepTimeMinutes) {
    this.name = name;
    this.ingredientNames = ingredientNames;
    this.instructions = instructions;
    this.prepTimeMinutes = prepTimeMinutes;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getIngredientNames() {
    return ingredientNames;
  }

  public void setIngredientNames(List<String> ingredientNames) {
    this.ingredientNames = ingredientNames;
  }

  public String getInstructions() {
    return instructions;
  }

  public void setInstructions(String instructions) {
    this.instructions = instructions;
  }

  public int getPrepTimeMinutes() {
    return prepTimeMinutes;
  }

  public void setPrepTimeMinutes(int prepTimeMinutes) {
    this.prepTimeMinutes = prepTimeMinutes;
  }
}
