package com.smartpantry.models;

import java.util.List;

public class User {

    private String username;
    private String email;
    private List<PantryItem> pantryItems;
    private List<Recipe> favoriteRecipes;

    public User() {
    }

    public User(String username, String email) {
        this.username = username;
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<PantryItem> getPantryItems() {
        return pantryItems;
    }

    public void setPantryItems(List<PantryItem> pantryItems) {
        this.pantryItems = pantryItems;
    }

    public List<Recipe> getFavoriteRecipes() {
        return favoriteRecipes;
    }

    public void setFavoriteRecipes(List<Recipe> favoriteRecipes) {
        this.favoriteRecipes = favoriteRecipes;
    }
}
