package com.smartpantry.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OgImageFetcher {

  private static final Pattern OG_IMAGE_PATTERN = Pattern.compile(
      "<meta[^>]+property=[\"']og:image(?::secure_url)?[\"'][^>]+content=[\"']([^\"']+)[\"']",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern OG_IMAGE_PATTERN_REVERSED = Pattern.compile(
      "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image(?::secure_url)?[\"']",
      Pattern.CASE_INSENSITIVE);

  private static final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
  private static final String NO_IMAGE = "";

  private static final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(8))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  private OgImageFetcher() {
  }

  public static String fetchImageUrl(String pageUrl) {
    if (pageUrl == null || pageUrl.isBlank()) {
      return null;
    }

    String cached = cache.get(pageUrl);
    if (cached != null) {
      return cached.equals(NO_IMAGE) ? null : cached;
    }

    String result = fetchFresh(pageUrl);
    cache.put(pageUrl, result == null ? NO_IMAGE : result);
    return result;
  }

  /**
   * Alias for fetchImageUrl(String) — kept so existing callers written
   * against the shorter name (e.g. OgImageFetcher.fetch(url)) still compile.
   */
  public static String fetch(String pageUrl) {
    return fetchImageUrl(pageUrl);
  }

  private static String fetchFresh(String pageUrl) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(pageUrl))
          .timeout(Duration.ofSeconds(8))
          .header("User-Agent", "Mozilla/5.0 (compatible; SmartPantryBot/1.0)")
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return null;
      }

      String html = response.body();
      if (html == null || html.isBlank()) {
        return null;
      }

      Matcher matcher = OG_IMAGE_PATTERN.matcher(html);
      if (matcher.find()) {
        return matcher.group(1);
      }

      Matcher reversed = OG_IMAGE_PATTERN_REVERSED.matcher(html);
      if (reversed.find()) {
        return reversed.group(1);
      }

      return null;
    } catch (Exception e) {
      System.err.println("OgImageFetcher error for " + pageUrl + ": " + e.getMessage());
      return null;
    }
  }

  public static void clearCache() {
    cache.clear();
  }
}
