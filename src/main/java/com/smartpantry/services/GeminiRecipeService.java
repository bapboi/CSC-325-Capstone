package com.smartpantry.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GeminiRecipeService {

  private static final String MODEL = "gemini-2.5-flash-lite";
  private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL
          + ":generateContent";

  private final HttpClient httpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(20))
          .build();
  private final Gson gson = new Gson();

  public RecipeResult findRecipe(List<String> pantryIngredients) throws GeminiException {
    String apiKey;
    try {
      apiKey = AppConfig.load().getGeminiApiKey();
    } catch (AppConfig.ConfigException e) {
      throw new GeminiException(e.getMessage());
    }

    String prompt = "I have these ingredients in my pantry: " + String.join(", ", pantryIngredients)
            + ". Search online and recommend ONE real recipe that uses as many of these "
            + "ingredients as possible. Reply with just the recipe name and a one sentence summary.";

    JsonObject body = buildRequestBody(prompt);

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonObject json = gson.fromJson(response.body(), JsonObject.class);

      if (response.statusCode() != 200) {
        String message = (json != null && json.has("error"))
                ? json.getAsJsonObject("error").get("message").getAsString()
                : "Gemini request failed (status " + response.statusCode() + ")";
        throw new GeminiException(message);
      }

      JsonArray candidates = json.getAsJsonArray("candidates");
      if (candidates == null || candidates.isEmpty()) {
        throw new GeminiException("Gemini returned no suggestions.");
      }
      JsonObject candidate = candidates.get(0).getAsJsonObject();

      String text = candidate.getAsJsonObject("content")
              .getAsJsonArray("parts")
              .get(0).getAsJsonObject()
              .get("text").getAsString();

      return new RecipeResult(text.trim(), extractFirstLink(candidate));
    } catch (GeminiException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiException("Network error: " + e.getMessage());
    }
  }

  private JsonObject buildRequestBody(String prompt) {
    JsonObject part = new JsonObject();
    part.addProperty("text", prompt);

    JsonArray parts = new JsonArray();
    parts.add(part);

    JsonObject content = new JsonObject();
    content.add("parts", parts);

    JsonArray contents = new JsonArray();
    contents.add(content);

    JsonObject googleSearchTool = new JsonObject();
    googleSearchTool.add("google_search", new JsonObject());

    JsonArray tools = new JsonArray();
    tools.add(googleSearchTool);

    JsonObject body = new JsonObject();
    body.add("contents", contents);
    body.add("tools", tools);
    return body;
  }

  private String extractFirstLink(JsonObject candidate) {
    if (!candidate.has("groundingMetadata"))
      return null;
    JsonObject groundingMetadata = candidate.getAsJsonObject("groundingMetadata");
    if (!groundingMetadata.has("groundingChunks"))
      return null;

    for (JsonElement chunkElement : groundingMetadata.getAsJsonArray("groundingChunks")) {
      JsonObject chunk = chunkElement.getAsJsonObject();
      if (chunk.has("web")) {
        JsonObject web = chunk.getAsJsonObject("web");
        if (web.has("uri")) {
          return web.get("uri").getAsString();
        }
      }
    }
    return null;
  }

  public record RecipeResult(String description, String link) {
  }

  public record IngredientAnalysis(List<String> usedPantryIngredients, List<String> missingIngredients) {
  }

  // gemini prompt using pure plaintext instead of google serach (hopefully saves
  // tokens)
  public IngredientAnalysis analyzeRecipeIngredients(String recipeDescription, List<String> pantryIngredients)
          throws GeminiException {
    String apiKey;
    try {
      apiKey = AppConfig.load().getGeminiApiKey();
    } catch (AppConfig.ConfigException e) {
      throw new GeminiException(e.getMessage());
    }

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

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonObject json = gson.fromJson(response.body(), JsonObject.class);

      if (response.statusCode() != 200) {
        String code = json.has("error")
                ? json.getAsJsonObject("error").get("message").getAsString()
                : "status " + response.statusCode();
        throw new GeminiException("Gemini error: " + code);
      }

      String text = json.getAsJsonArray("candidates")
              .get(0).getAsJsonObject()
              .getAsJsonObject("content")
              .getAsJsonArray("parts")
              .get(0).getAsJsonObject()
              .get("text").getAsString().trim();

      // strip markdown fences if gemini wrapped the json in them
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
    } catch (GeminiException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiException("Network error: " + e.getMessage());
    }
  }

  public List<String> findMissingIngredients(String recipeDescription, List<String> pantryIngredients)
          throws GeminiException {
    return analyzeRecipeIngredients(recipeDescription, pantryIngredients).missingIngredients();
  }

  public static class GeminiException extends Exception {
    public GeminiException(String message) {
      super(message);
    }
  }

  /**
   * Sends a base64-encoded image to Gemini and asks it to identify the
   * ingredient, returning a JsonObject with fields:
   * name, category, quantity, unit, detectedByAI
   */
  public com.google.gson.JsonObject identifyIngredientFromImage(String imageBase64, String mimeType)
          throws GeminiException {

    String apiKey;
    try {
      apiKey = AppConfig.load().getGeminiApiKey();
    } catch (AppConfig.ConfigException e) {
      throw new GeminiException(e.getMessage());
    }

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
                    + "Rules:\n"
                    + "- name: common ingredient name (e.g. \"Tomato\", \"Chicken Breast\")\n"
                    + "- category: one of: Fruit, Vegetable, Meat, Dairy, Grain, Spice, Beverage, Other\n"
                    + "- quantity: estimate a reasonable default amount visible (default 1 if unsure)\n"
                    + "- unit: e.g. pcs, g, kg, ml, cups — empty string if not applicable\n"
                    + "- detectedByAI: always true\n"
                    + "- Return ONLY the JSON object, nothing else"
    );

    JsonArray parts = new JsonArray();
    parts.add(imagePart);
    parts.add(textPart);

    JsonObject content = new JsonObject();
    content.add("parts", parts);

    JsonArray contents = new JsonArray();
    contents.add(content);

    JsonObject body = new JsonObject();
    body.add("contents", contents);

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonObject json = gson.fromJson(response.body(), JsonObject.class);

      if (response.statusCode() != 200) {
        String code = json.has("error")
                ? json.getAsJsonObject("error").get("message").getAsString()
                : "status " + response.statusCode();
        throw new GeminiException("Gemini error: " + code);
      }

      String rawText = json.getAsJsonArray("candidates")
              .get(0).getAsJsonObject()
              .getAsJsonObject("content")
              .getAsJsonArray("parts")
              .get(0).getAsJsonObject()
              .get("text").getAsString().trim();

      String clean = rawText.replaceAll("(?s)```json\\s*|```", "").trim();
      int start = clean.indexOf('{');
      int end   = clean.lastIndexOf('}');
      if (start == -1 || end == -1) {
        throw new GeminiException("No valid JSON found in Gemini response");
      }

      return gson.fromJson(clean.substring(start, end + 1), JsonObject.class);

    } catch (GeminiException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiException("Network error: " + e.getMessage());
    }
  }
}
