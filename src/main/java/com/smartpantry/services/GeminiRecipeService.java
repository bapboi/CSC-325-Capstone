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

  // gemini prompt using pure plaintext instead of google serach (hopefully saves
  // tokens)
  public List<String> findMissingIngredients(String recipeDescription, List<String> pantryIngredients)
      throws GeminiException {
    String apiKey;
    try {
      apiKey = AppConfig.load().getGeminiApiKey();
    } catch (AppConfig.ConfigException e) {
      throw new GeminiException(e.getMessage());
    }

    String prompt = "This recipe was suggested: \"" + recipeDescription + "\". "
        + "The user already has these ingredients: " + String.join(", ", pantryIngredients) + ". "
        + "List ONLY the ingredient names that are missing from what the user has. "
        + "Return them as a JSON array of strings, nothing else. Example: [\"flour\",\"eggs\"]";

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

      JsonArray arr = gson.fromJson(text, JsonArray.class);
      List<String> result = new java.util.ArrayList<>();
      for (JsonElement el : arr)
        result.add(el.getAsString());
      return result;
    } catch (GeminiException e) {
      throw e;
    } catch (Exception e) {
      throw new GeminiException("Network error: " + e.getMessage());
    }
  }

  public static class GeminiException extends Exception {
    public GeminiException(String message) {
      super(message);
    }
  }
}
