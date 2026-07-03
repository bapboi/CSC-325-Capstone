package com.smartpantry.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConfig {

  private static final Path CONFIG_FILE = Paths.get("app-config.json");
  private static AppConfig instance;

  private final String webApiKey;
  private final String geminiApiKey;

  private AppConfig(String webApiKey, String geminiApiKey) {
    this.webApiKey = webApiKey;
    this.geminiApiKey = geminiApiKey;
  }

  public static synchronized AppConfig load() throws ConfigException {
    if (instance != null)
      return instance;

    if (!Files.exists(CONFIG_FILE)) {
      throw new ConfigException(
          "app-config.json not found. Create a file named app-config.json next to pom.xml - "
              + "see app-config.example.json for the expected format.");
    }
    // bake example config

    try {
      String content = Files.readString(CONFIG_FILE);
      JsonObject json = new Gson().fromJson(content, JsonObject.class);

      String webKey = json.has("webApiKey") ? json.get("webApiKey").getAsString() : null;
      if (webKey == null || webKey.isBlank()) {
        throw new ConfigException("app-config.json is missing a \"webApiKey\" value.");
      }

      String geminiKey = json.has("geminiApiKey") ? json.get("geminiApiKey").getAsString() : null;

      instance = new AppConfig(webKey, geminiKey);
      return instance;
    } catch (IOException e) {
      throw new ConfigException("Could not read app-config.json: " + e.getMessage());
    }
  }

  public String getWebApiKey() {
    return webApiKey;
  }

  public String getGeminiApiKey() throws ConfigException {
    if (geminiApiKey == null || geminiApiKey.isBlank()) {
      throw new ConfigException(
          "app-config.json is missing a \"geminiApiKey\" value. "
              + "Get one from Google AI Studio and add it to use recipe search.");
    }
    return geminiApiKey;
  }

  public static class ConfigException extends Exception {
    public ConfigException(String message) {
      super(message);
    }
  }
}
