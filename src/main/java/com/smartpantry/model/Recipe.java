package com.smartpantry.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Recipe {

    private String id; // Firestore document id
    private String name;
    private String imageUrl;
    private String cookTime;
    private String difficulty;
    private int servings;
    private String sourceLink;
    private String userID;

    private List<String> ingredients = new ArrayList<>();
    private List<String> pantryIngredients = new ArrayList<>();
    private List<String> missingIngredients = new ArrayList<>();
    private List<String> instructions = new ArrayList<>();

    public Recipe() {
    }

    public Recipe(String name) {
        this.name = name;
    }

    public int getPantryMatchPercent() {
        int total = pantryIngredients.size() + missingIngredients.size();
        if (total == 0) return 0;
        return (int) Math.round((pantryIngredients.size() * 100.0) / total);
    }

    public String getInstructionsAsText() {
        if (instructions == null || instructions.isEmpty()) return "Instructions not available.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < instructions.size(); i++) {
            sb.append(i + 1).append(". ").append(instructions.get(i)).append("\n");
        }
        return sb.toString();
    }

    /** Converts this Recipe into a Firestore-compatible map. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("cookTime", cookTime);
        map.put("difficulty", difficulty);
        map.put("servings", servings);
        map.put("sourceLink", sourceLink);
        map.put("imageUrl", imageUrl);
        map.put("userID", userID);
        map.put("ingredients", ingredients != null ? ingredients : new ArrayList<>());
        map.put("pantryIngredients", pantryIngredients != null ? pantryIngredients : new ArrayList<>());
        map.put("missingIngredients", missingIngredients != null ? missingIngredients : new ArrayList<>());
        map.put("instructions", instructions != null ? instructions : new ArrayList<>());
        return map;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getCookTime() { return cookTime; }
    public void setCookTime(String cookTime) { this.cookTime = cookTime; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public int getServings() { return servings; }
    public void setServings(int servings) { this.servings = servings; }
    public String getSourceLink() { return sourceLink; }
    public void setSourceLink(String sourceLink) { this.sourceLink = sourceLink; }
    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }
    public List<String> getIngredients() { return ingredients; }
    public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }
    public List<String> getPantryIngredients() { return pantryIngredients; }
    public void setPantryIngredients(List<String> pantryIngredients) { this.pantryIngredients = pantryIngredients; }
    public List<String> getMissingIngredients() { return missingIngredients; }
    public void setMissingIngredients(List<String> missingIngredients) { this.missingIngredients = missingIngredients; }
    public List<String> getInstructions() { return instructions; }
    public void setInstructions(List<String> instructions) { this.instructions = instructions; }
}
