package com.smartpantry.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * File-based cache for Gemini recipe results.
 * Stored at ~/.smartpantry/recipe-cache.json.
 * Keyed by sorted, comma-joined pantry ingredient names.
 * Entries expire after EXPIRY_HOURS hours.
 * Supports appending additional recipes for the "Load More" feature.
 */
public class RecipeCache {

    private static final Path CACHE_FILE =
            Paths.get(System.getProperty("user.home"), ".smartpantry", "recipe-cache.json");
    private static final long EXPIRY_HOURS = 24;
    private static final Gson gson = new Gson();

    public static String keyFor(List<String> ingredientNames) {
        return ingredientNames.stream()
                .map(String::toLowerCase)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * Returns the cached JSON array string, or null if missing/expired.
     */
    public static String get(String key) {
        if (!Files.exists(CACHE_FILE)) return null;
        try {
            JsonObject root = readRoot();
            if (root == null || !root.has(key)) return null;

            JsonObject entry = root.getAsJsonObject(key);
            long timestamp = entry.get("timestamp").getAsLong();
            long ageHours  = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60);
            if (ageHours >= EXPIRY_HOURS) {
                root.remove(key);
                writeRoot(root);
                return null;
            }
            return entry.get("recipes").getAsString();
        } catch (Exception e) {
            System.err.println("RecipeCache read error: " + e.getMessage());
            return null;
        }
    }

    /** Stores a JSON array string under the given key. */
    public static void put(String key, String recipesJson) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            JsonObject root = readRoot();
            if (root == null) root = new JsonObject();

            JsonObject entry = new JsonObject();
            entry.addProperty("timestamp", System.currentTimeMillis());
            entry.addProperty("recipes", recipesJson);
            root.add(key, entry);
            writeRoot(root);
        } catch (IOException e) {
            System.err.println("RecipeCache write error: " + e.getMessage());
        }
    }

    /**
     * Appends additional recipes to an existing cache entry.
     * Used by "Load More" so the expanded list is also cached.
     */
    public static void append(String key, String moreRecipesJson) {
        try {
            String existing = get(key);
            if (existing == null) {
                put(key, moreRecipesJson);
                return;
            }
            // Merge the two JSON arrays
            JsonArray base = gson.fromJson(existing, JsonArray.class);
            JsonArray more = gson.fromJson(moreRecipesJson, JsonArray.class);
            for (JsonElement el : more) base.add(el);
            put(key, gson.toJson(base));
        } catch (Exception e) {
            System.err.println("RecipeCache append error: " + e.getMessage());
        }
    }

    public static void clear() {
        try { if (Files.exists(CACHE_FILE)) Files.delete(CACHE_FILE); }
        catch (IOException e) { System.err.println("RecipeCache clear error: " + e.getMessage()); }
    }

    private static JsonObject readRoot() {
        try {
            if (!Files.exists(CACHE_FILE)) return new JsonObject();
            String content = Files.readString(CACHE_FILE);
            JsonObject root = gson.fromJson(content, JsonObject.class);
            return root != null ? root : new JsonObject();
        } catch (Exception e) { return new JsonObject(); }
    }

    private static void writeRoot(JsonObject root) throws IOException {
        Files.writeString(CACHE_FILE, gson.toJson(root));
    }
}
