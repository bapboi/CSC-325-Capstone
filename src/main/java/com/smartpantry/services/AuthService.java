package com.smartpantry.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AuthService {

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final Gson gson = new Gson();

  public AuthResult signUp(String email, String password) throws AuthException {
    return callIdentityToolkit("accounts:signUp", email, password);
  }

  public AuthResult signIn(String email, String password) throws AuthException {
    return callIdentityToolkit("accounts:signInWithPassword", email, password);
  }

  private AuthResult callIdentityToolkit(String endpoint, String email, String password) throws AuthException {
    String apiKey;
    try {
      apiKey = AppConfig.load().getWebApiKey();
    } catch (AppConfig.ConfigException e) {
      throw new AuthException(e.getMessage());
    }
    String url = "https://identitytoolkit.googleapis.com/v1/" + endpoint + "?key=" + apiKey;

    JsonObject body = new JsonObject();
    body.addProperty("email", email);
    body.addProperty("password", password);
    body.addProperty("returnSecureToken", true);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonObject json = gson.fromJson(response.body(), JsonObject.class);

      if (response.statusCode() != 200) {
        String code = json.has("error")
            ? json.getAsJsonObject("error").get("message").getAsString()
            : "UNKNOWN_ERROR";
        throw new AuthException(friendlyMessage(code));
      }

      return new AuthResult(
          json.get("localId").getAsString(),
          json.get("email").getAsString(),
          json.get("idToken").getAsString());
    } catch (AuthException e) {
      throw e;
    } catch (Exception e) {
      throw new AuthException("Network error: " + e.getMessage());
    }
  }

  private String friendlyMessage(String firebaseCode) {
    if (firebaseCode.startsWith("WEAK_PASSWORD")) {
      return "Password must be at least 6 characters.";
    }
    return switch (firebaseCode) {
      case "EMAIL_EXISTS" -> "An account with that email already exists.";
      case "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" ->
        "Incorrect email or password.";
      case "INVALID_EMAIL" -> "That doesn't look like a valid email address.";
      default -> firebaseCode;
    };
  }

  public record AuthResult(String uid, String email, String idToken) {
  }

  public static class AuthException extends Exception {
    public AuthException(String message) {
      super(message);
    }
  }
}
