package com.smartpantry.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a reliable thumbnail image URL for a recipe by reading the
 * Open Graph ("og:image") meta tag from the recipe's source page.
 *
 * Gemini-provided imageUrl values are frequently missing, expired, or
 * point at pages rather than direct image files, so this is used as a
 * fallback whenever a recipe's own imageUrl fails to load.
 *
 * Results are cached in-memory (per source URL) since the same recipe
 * page may be looked up multiple times across the suggestion list and
 * the details screen.
 */
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

  /**
   * Returns a direct image URL scraped from the page's og:image meta tag,
   * or null if the page has none or could not be fetched.
   *
   * Safe to call from a background thread only — this makes a blocking
   * network request.
   */
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

  /** Clears the in-memory og:image cache. */
  public static void clearCache() {
    cache.clear();
  }
}
