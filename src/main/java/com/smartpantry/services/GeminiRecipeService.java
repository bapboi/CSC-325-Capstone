package com.smartpantry.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.smartpantry.model.Recipe;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GeminiRecipeService {

  private static final String MODEL = "gemini-3.1-flash-lite";
  private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/"
      + MODEL + ":generateContent";

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20))
      .build();
  private final Gson gson = new Gson();

  public List<Recipe> findRecipesByPantry(List<String> pantryIngredients) throws GeminiException {
    if (pantryIngredients.isEmpty()) {
      throw new GeminiException("Your pantry is empty — add some ingredients first.");
    }

    String cacheKey = RecipeCache.keyFor(pantryIngredients);
    String cached = RecipeCache.get(cacheKey);
    if (cached != null) {
      System.out.println("RecipeCache hit for " + pantryIngredients.size() + " ingredients");
      return parseRecipesJson(cached);
    }

    String recipesJson = callGeminiForRecipes(pantryIngredients, List.of(), 5);
    List<Recipe> result = parseRecipesJson(recipesJson);

    if (!result.isEmpty()) {
      RecipeCache.put(cacheKey, recipesJson);
    }

    return result;
  }

  public List<Recipe> findMoreRecipes(List<String> pantryIngredients,
      List<String> alreadyShownNames) throws GeminiException {
    if (pantryIngredients.isEmpty()) {
      throw new GeminiException("Your pantry is empty.");
    }
    String moreJson = callGeminiForRecipes(pantryIngredients, alreadyShownNames, 5);
    List<Recipe> result = parseRecipesJson(moreJson);

    if (!result.isEmpty()) {
      String cacheKey = RecipeCache.keyFor(pantryIngredients);
      RecipeCache.append(cacheKey, moreJson);
    }

    return result;
  }

  // single recipe lookup
  public RecipeResult findRecipe(List<String> pantryIngredients) throws GeminiException {
    List<Recipe> recipes = findRecipesByPantry(pantryIngredients);
    if (recipes.isEmpty())
      throw new GeminiException("Gemini returned no recipes.");
    Recipe r = recipes.get(0);
    return new RecipeResult(
        r.getName() + (r.getDescription() != null && !r.getDescription().isBlank()
            ? " — " + r.getDescription()
            : ""),
        r.getSourceLink());
  }

  public JsonObject identifyIngredientFromImage(String imageBase64, String mimeType)
      throws GeminiException {

    String apiKey = getApiKey();

    JsonObject inlineData = new JsonObject();
    inlineData.addProperty("mime_type", mimeType);
    inlineData.addProperty("data", imageBase64);

    JsonObject imagePart = new JsonObject();
    imagePart.add("inline_data", inlineData);

    JsonObject textPart = new JsonObject();
    textPart.addProperty("text",
        "You are a pantry ingredient scanner.\n\n"
            + "Identify the ingredient in this image.\n\n"
            + "Return STRICT JSON ONLY — no markdown, no backticks, no extra text:\n\n"
            + "{\n"
            + "  \"name\": \"\",\n"
            + "  \"category\": \"\",\n"
            + "  \"quantity\": 1,\n"
            + "  \"unit\": \"\",\n"
            + "  \"detectedByAI\": true\n"
            + "}\n\n"
            + "- category: one of Fruit, Vegetable, Meat, Dairy, Grain, Spice, Beverage, Other\n"
            + "- quantity: best estimate you can see, default 1 if unsure\n"
            + "- unit: e.g. pcs, g, kg, ml, cups — empty string if not applicable\n"
            + "- Return ONLY the JSON object, nothing else");

    JsonArray parts = new JsonArray();
    parts.add(imagePart);
    parts.add(textPart);
    JsonObject content = new JsonObject();
    content.add("parts", parts);
    JsonArray contents = new JsonArray();
    contents.add(content);
    JsonObject body = new JsonObject();
    body.add("contents", contents);
    body.add("generationConfig", defaultGenerationConfig());

    return (JsonObject) callGeminiRaw(body, apiKey, false);
  }

  public List<String> findMissingIngredients(String recipeDescription,
      List<String> pantryIngredients) throws GeminiException {
    String apiKey = getApiKey();
    String prompt = "This recipe was suggested: \"" + recipeDescription + "\". "
        + "The user already has: " + String.join(", ", pantryIngredients) + ". "
        + "List ONLY the ingredient names that are missing. "
        + "Return a JSON array of strings, nothing else. Example: [\"flour\",\"eggs\"]";

    JsonObject part = new JsonObject();
    part.addProperty("text", prompt);
    JsonArray parts = new JsonArray();
    parts.add(part);
    JsonObject content = new JsonObject();
    content.add("parts", parts);
    JsonArray contents = new JsonArray();
    contents.add(content);
    JsonObject body = new JsonObject();
    body.add("contents", contents);
    body.add("generationConfig", defaultGenerationConfig());

    String rawText = (String) callGeminiRaw(body, apiKey, true);
    String clean = rawText.replaceAll("(?s)```json\\s*|```", "").trim();
    JsonArray arr = gson.fromJson(clean, JsonArray.class);
    List<String> result = new ArrayList<>();
    for (JsonElement el : arr)
      result.add(el.getAsString());
    return result;
  }

  // ── Ingredient analysis (alternate single-call API) ───────────────────────

  public record IngredientAnalysis(List<String> usedPantryIngredients, List<String> missingIngredients) {
  }

  // gemini prompt using pure plaintext instead of google search (hopefully
  // saves tokens)
  public IngredientAnalysis analyzeRecipeIngredients(String recipeDescription,
      List<String> pantryIngredients) throws GeminiException {
    String apiKey = getApiKey();
    String prompt = "This recipe was suggested: \"" + recipeDescription + "\". "
        + "The user already has these ingredients: " + String.join(", ", pantryIngredients) + ". "
        + "Return a JSON object with exactly two fields: "
        + "\"usedPantryIngredients\" for pantry ingredients actually used in the recipe, and "
        + "\"missingIngredients\" for recipe ingredients the user does not have. "
        + "Only include ingredient names with no quantities or explanations. "
        + "Return JSON only. Example: "
        + "{\"usedPantryIngredients\":[\"eggs\",\"milk\"],"
        + "\"missingIngredients\":[\"flour\",\"butter\"]}";

    JsonObject part = new JsonObject();
    part.addProperty("text", prompt);
    JsonArray parts = new JsonArray();
    parts.add(part);
    JsonObject content = new JsonObject();
    content.add("parts", parts);
    JsonArray contents = new JsonArray();
    contents.add(content);
    JsonObject body = new JsonObject();
    body.add("contents", contents);
    body.add("generationConfig", defaultGenerationConfig());

    String text = (String) callGeminiRaw(body, apiKey, true);
    text = text.replaceAll("(?s)```json\\s*|```", "").trim();
    JsonObject result = gson.fromJson(text, JsonObject.class);

    List<String> usedPantryIngredients = new ArrayList<>();
    List<String> missingIngredients = new ArrayList<>();

    if (result.has("usedPantryIngredients")) {
      for (JsonElement el : result.getAsJsonArray("usedPantryIngredients"))
        usedPantryIngredients.add(el.getAsString());
    }

    if (result.has("missingIngredients")) {
      for (JsonElement el : result.getAsJsonArray("missingIngredients"))
        missingIngredients.add(el.getAsString());
    }

    return new IngredientAnalysis(usedPantryIngredients, missingIngredients);
  }

  // --- helpers -------------------------------------
  private String callGeminiForRecipes(List<String> pantryIngredients,
      List<String> excludeNames,
      int count) throws GeminiException {
    String apiKey = getApiKey();
    String ingredientList = String.join(", ", pantryIngredients);

    String excludeClause = excludeNames.isEmpty() ? ""
        : "Do NOT suggest any of these recipes (already shown): "
            + String.join(", ", excludeNames) + ".\n\n";

    String prompt = "I have these ingredients in my pantry: " + ingredientList + ".\n\n"
        + excludeClause
        + "Suggest exactly " + count + " real recipes. "
        + "You do NOT need to use only pantry ingredients — "
        + "it is fine if a recipe needs a few extra items. "
        + "Prioritise recipes that use the most pantry ingredients, "
        + "but still suggest great recipes even if some ingredients are missing.\n\n"
        + "Search the web for real, well-known recipes with actual pages.\n\n"
        + "Return ONLY a JSON array — no markdown, no backticks, no extra text:\n\n"
        + "[\n"
        + "  {\n"
        + "    \"name\": \"Recipe Name\",\n"
        + "    \"description\": \"One sentence about the dish\",\n"
        + "    \"prepTime\": \"10 mins\",\n"
        + "    \"cookTime\": \"20 mins\",\n"
        + "    \"difficulty\": \"Easy\",\n"
        + "    \"servings\": 4,\n"
        + "    \"imageUrl\": \"https://direct-link-to-recipe-image.jpg\",\n"
        + "    \"sourceLink\": \"https://real-recipe-page-url.com\",\n"
        + "    \"pantryIngredients\": [\"ingredients from MY pantry list used here\"],\n"
        + "    \"missingIngredients\": [\"ingredients needed but NOT in my pantry\"],\n"
        + "    \"ingredients\": [\"full list with quantities e.g. 2 cups flour\"],\n"
        + "    \"instructions\": [\"Step 1: ...\", \"Step 2: ...\"]\n"
        + "  }\n"
        + "]\n\n"
        + "Rules:\n"
        + "- pantryIngredients: ONLY items from my pantry list above\n"
        + "- missingIngredients: items the recipe needs that I do NOT have\n"
        + "- imageUrl: a real direct .jpg or .png image URL from the recipe page\n"
        + "- sourceLink: the real URL of the recipe page\n"
        + "- difficulty: Easy, Medium, or Hard only\n"
        + "- Return ONLY the JSON array";

    JsonObject body = buildBodyWithSearch(prompt);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(ENDPOINT))
        .header("Content-Type", "application/json")
        .header("x-goog-api-key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonObject json = gson.fromJson(response.body(), JsonObject.class);
      if (json.has("error")) {
        String msg = json.getAsJsonObject("error").get("message").getAsString();
        throw new GeminiException("Gemini error: " + msg);
      }
      if (response.statusCode() != 200) {
        throw new GeminiException("Gemini returned status " + response.statusCode());
      }

      JsonArray candidates = json.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        String reason = "Gemini returned no results.";
        if (json.has("promptFeedback")) {
          reason += " Reason: " + json.get("promptFeedback").toString();
        }
        throw new GeminiException(reason
            + " You may have exceeded your Gemini quota — try again later.");
      }

      String rawText = extractCandidateText(candidates.get(0).getAsJsonObject());

      String clean = rawText.replaceAll("(?s)```json\\s*|```", "").trim();
      int start = clean.indexOf('[');
      int end = clean.lastIndexOf(']');
      if (start == -1 || end == -1) {
        throw new GeminiException(
            "Gemini response was not valid JSON. Raw: " + clean.substring(0, Math.min(200, clean.length())));
      }
      return clean.substring(start, end + 1);

    } catch (GeminiException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiException("Network error: " + e.getMessage());
    }
  }

  private Object callGeminiRaw(JsonObject body, String apiKey,
      boolean returnText) throws GeminiException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(ENDPOINT))
        .header("Content-Type", "application/json")
        .header("x-goog-api-key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
        .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonObject json = gson.fromJson(response.body(), JsonObject.class);

      if (json.has("error")) {
        throw new GeminiException("Gemini error: "
            + json.getAsJsonObject("error").get("message").getAsString());
      }
      if (response.statusCode() != 200) {
        throw new GeminiException("Gemini returned status " + response.statusCode());
      }

      JsonArray candidates = json.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        throw new GeminiException(
            "Gemini returned no results. You may have exceeded your quota.");
      }

      String rawText = extractCandidateText(candidates.get(0).getAsJsonObject());

      if (returnText)
        return rawText;

      String clean = rawText.replaceAll("(?s)```json\\s*|```", "").trim();
      int start = clean.indexOf('{');
      int end = clean.lastIndexOf('}');
      if (start == -1 || end == -1) {
        throw new GeminiException("No valid JSON object in Gemini response.");
      }
      return gson.fromJson(clean.substring(start, end + 1), JsonObject.class);

    } catch (GeminiException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiException("Network error: " + e.getMessage());
    }
  }

  // gemini candidate parsing to fix null return
  private String extractCandidateText(JsonObject candidate) throws GeminiException {
    JsonObject content = candidate.has("content") && candidate.get("content").isJsonObject()
        ? candidate.getAsJsonObject("content")
        : null;

    JsonArray parts = (content != null && content.has("parts") && content.get("parts").isJsonArray())
        ? content.getAsJsonArray("parts")
        : null;

    if (parts == null || parts.size() == 0) {
      String finishReason = candidate.has("finishReason")
          ? candidate.get("finishReason").getAsString()
          : "unknown";
      throw new GeminiException(
          "Gemini returned an empty response (finishReason: " + finishReason + "). "
              + "This usually means it ran out of output tokens or the response was filtered — try again.");
    }

    JsonObject firstPart = parts.get(0).getAsJsonObject();
    if (!firstPart.has("text")) {
      throw new GeminiException("Gemini response part had no text field.");
    }

    return firstPart.get("text").getAsString().trim();
  }

  private List<Recipe> parseRecipesJson(String json) {
    List<Recipe> recipes = new ArrayList<>();
    JsonArray arr = gson.fromJson(json, JsonArray.class);
    for (JsonElement el : arr) {
      JsonObject obj = el.getAsJsonObject();
      Recipe recipe = new Recipe();
      recipe.setName(s(obj, "name"));
      recipe.setDescription(s(obj, "description"));
      recipe.setCookTime(s(obj, "cookTime"));
      recipe.setPrepTime(s(obj, "prepTime"));
      recipe.setDifficulty(s(obj, "difficulty"));
      recipe.setSourceLink(s(obj, "sourceLink"));
      recipe.setImageUrl(s(obj, "imageUrl"));
      recipe.setServings(obj.has("servings") ? obj.get("servings").getAsInt() : 0);
      recipe.setIngredients(toList(obj, "ingredients"));
      recipe.setPantryIngredients(toList(obj, "pantryIngredients"));
      recipe.setMissingIngredients(toList(obj, "missingIngredients"));
      recipe.setInstructions(toList(obj, "instructions"));
      recipes.add(recipe);
    }
    return recipes;
  }

  private String s(JsonObject obj, String key) {
    return (obj.has(key) && !obj.get(key).isJsonNull())
        ? obj.get(key).getAsString()
        : "";
  }

  private List<String> toList(JsonObject obj, String key) {
    List<String> list = new ArrayList<>();
    if (!obj.has(key) || obj.get(key).isJsonNull())
      return list;
    for (JsonElement el : obj.getAsJsonArray(key))
      list.add(el.getAsString());
    return list;
  }

  private JsonObject buildBodyWithSearch(String prompt) {
    JsonObject part = new JsonObject();
    part.addProperty("text", prompt);
    JsonArray parts = new JsonArray();
    parts.add(part);
    JsonObject content = new JsonObject();
    content.add("parts", parts);
    JsonArray contents = new JsonArray();
    contents.add(content);
    JsonObject googleSearch = new JsonObject();
    googleSearch.add("google_search", new JsonObject());
    JsonArray tools = new JsonArray();
    tools.add(googleSearch);
    JsonObject body = new JsonObject();
    body.add("contents", contents);
    body.add("tools", tools);
    body.add("generationConfig", defaultGenerationConfig());
    return body;
  }

  // generation config for gemini flash
  private JsonObject defaultGenerationConfig() {
    JsonObject thinkingConfig = new JsonObject();
    thinkingConfig.addProperty("thinkingBudget", 0);

    JsonObject generationConfig = new JsonObject();
    generationConfig.addProperty("maxOutputTokens", 4096);
    generationConfig.add("thinkingConfig", thinkingConfig);
    return generationConfig;
  }

  private String getApiKey() throws GeminiException {
    try {
      return AppConfig.load().getGeminiApiKey();
    } catch (AppConfig.ConfigException e) {
      throw new GeminiException(e.getMessage());
    }
  }

  public record RecipeResult(String description, String link) {
  }

  public static class GeminiException extends Exception {
    public GeminiException(String message) {
      super(message);
    }
  }
}
