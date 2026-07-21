package com.smartpantry.services;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Fetches recipe page thumbnails reliably by:
 *  1. Scraping the og:image meta tag from the recipe page.
 *  2. Downloading image bytes with a realistic browser User-Agent + Referer
 *     header (CDNs reject hotlinked requests without Referer).
 *  3. Requesting JPEG/PNG explicitly — avoids getting WebP/AVIF which
 *     JavaFX 21 cannot decode natively.
 *  4. If the server sends WebP anyway, converting to JPEG via TwelveMonkeys
 *     ImageIO (registered automatically on the classpath by its SPI entry).
 *
 * All results (including failures) are cached in memory so scrolling never
 * re-fetches the same page.
 */
public class OgImageFetcher {

    // Covers both attribute orderings:
    //   <meta property="og:image" content="...">
    //   <meta content="..." property="og:image">
    private static final Pattern OG_PROPERTY_FIRST = Pattern.compile(
            "<meta[^>]+property=[\"']og:image(?::secure_url)?[\"'][^>]+content=[\"']([^\"'<>]+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_CONTENT_FIRST = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"'<>]+)[\"'][^>]+property=[\"']og:image(?::secure_url)?[\"']",
            Pattern.CASE_INSENSITIVE);

    // Sentinel: cached miss (page had no og:image or was unreachable)
    private static final String  NO_IMAGE = "";
    private static final byte[]  NO_BYTES = new byte[0];

    private static final ConcurrentHashMap<String, String> urlCache   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, byte[]> bytesCache = new ConcurrentHashMap<>();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private OgImageFetcher() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves the best thumbnail for a recipe.
     * Tries the scraped og:image first; falls back to the Gemini-supplied imageUrl.
     * Returns null if neither produces downloadable image bytes.
     */
    public static byte[] resolveThumbnailBytes(String geminiImageUrl, String sourceLink) {
        // 1. Scrape the real page's og:image
        String scraped = fetchImageUrl(sourceLink);
        String primary = (scraped != null && !scraped.isBlank()) ? scraped : geminiImageUrl;

        // 2. Download whichever won
        byte[] bytes = fetchImageBytes(primary, sourceLink);
        if (bytes != null) {
            System.out.println("OgImageFetcher: resolved thumbnail for " + sourceLink
                    + " via " + (primary.equals(scraped) ? "scraped og:image" : "Gemini imageUrl"));
            return bytes;
        }

        // 3. If that failed and there's a second option, try it
        if (primary != null && !primary.equals(geminiImageUrl) && geminiImageUrl != null && !geminiImageUrl.isBlank()) {
            byte[] fallbackBytes = fetchImageBytes(geminiImageUrl, sourceLink);
            if (fallbackBytes != null) {
                System.out.println("OgImageFetcher: resolved thumbnail for " + sourceLink + " via Gemini imageUrl fallback");
            } else {
                System.err.println("OgImageFetcher: BOTH sources failed for " + sourceLink
                        + " (scraped=" + scraped + ", geminiImageUrl=" + geminiImageUrl + ")");
            }
            return fallbackBytes;
        }

        System.err.println("OgImageFetcher: no usable image source at all for " + sourceLink
                + " (geminiImageUrl=" + geminiImageUrl + ")");
        return null;
    }

    /** Fetches the og:image URL from the given page (cached). */
    public static String fetchImageUrl(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) return null;
        String cached = urlCache.get(pageUrl);
        if (cached != null) return cached.equals(NO_IMAGE) ? null : cached;
        String result = scrapeOgImage(pageUrl);
        urlCache.put(pageUrl, result == null ? NO_IMAGE : result);
        return result;
    }

    /** Alias kept for any existing callers. */
    public static String fetch(String pageUrl) { return fetchImageUrl(pageUrl); }

    /** Downloads raw image bytes for the given URL (cached). */
    public static byte[] fetchImageBytes(String imageUrl, String refererUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String key    = imageUrl + "|" + refererUrl;
        byte[] cached = bytesCache.get(key);
        if (cached != null) return cached == NO_BYTES ? null : cached;
        byte[] result = downloadImageBytes(imageUrl, refererUrl);
        bytesCache.put(key, result == null ? NO_BYTES : result);
        return result;
    }

    public static void clearCache() {
        urlCache.clear();
        bytesCache.clear();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static String scrapeOgImage(String pageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pageUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.9")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("OgImageFetcher scrape: HTTP " + response.statusCode() + " for " + pageUrl);
                return null;
            }

            String html = response.body();
            if (html == null || html.isBlank()) {
                System.err.println("OgImageFetcher scrape: empty body for " + pageUrl);
                return null;
            }

            // Only scan <head> — much faster, og:image is always in the head
            int headEnd = html.indexOf("</head>");
            String head = headEnd > 0
                    ? html.substring(0, headEnd)
                    : html.substring(0, Math.min(html.length(), 10_000));

            Matcher m = OG_PROPERTY_FIRST.matcher(head);
            if (m.find()) return m.group(1);

            Matcher m2 = OG_CONTENT_FIRST.matcher(head);
            if (m2.find()) return m2.group(1);

            System.err.println("OgImageFetcher scrape: no og:image tag found on " + pageUrl);
            return null;
        } catch (Exception e) {
            System.err.println("OgImageFetcher scrape error [" + pageUrl + "]: " + e.getMessage());
            return null;
        }
    }

    private static byte[] downloadImageBytes(String imageUrl, String refererUrl) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/124.0.0.0 Safari/537.36")
                    // Prefer JPEG/PNG — JavaFX 21 can decode these natively.
                    // WebP/AVIF need TwelveMonkeys conversion (handled below).
                    .header("Accept", "image/jpeg,image/png,image/gif,image/webp,image/*;q=0.8")
                    .GET();

            if (refererUrl != null && !refererUrl.isBlank()) {
                builder.header("Referer", refererUrl);
            }

            HttpResponse<byte[]> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                System.err.println("OgImageFetcher download: HTTP " + response.statusCode() + " for " + imageUrl);
                return null;
            }

            String contentType = response.headers()
                    .firstValue("Content-Type").orElse("").toLowerCase();
            if (!contentType.startsWith("image/")) {
                System.err.println("OgImageFetcher download: non-image Content-Type '"
                        + contentType + "' for " + imageUrl);
                return null;
            }

            byte[] body = response.body();
            if (body == null || body.length == 0) {
                System.err.println("OgImageFetcher download: empty body for " + imageUrl);
                return null;
            }

            // If it's WebP/AVIF, JavaFX 21 can't decode it directly.
            // TwelveMonkeys ImageIO (on classpath) adds WebP support to
            // javax.imageio.ImageIO — convert to JPEG bytes here so
            // the caller always gets something JavaFX can display.
            if (contentType.contains("webp") || contentType.contains("avif") || isWebP(body)) {
                System.err.println("OgImageFetcher download: converting WebP/AVIF -> JPEG for " + imageUrl);
                byte[] converted = convertToJpeg(body);
                if (converted == null) {
                    System.err.println("OgImageFetcher download: WebP/AVIF conversion FAILED for " + imageUrl);
                }
                return converted;
            }

            return body;
        } catch (Exception e) {
            System.err.println("OgImageFetcher download error [" + imageUrl + "]: " + e.getMessage());
            return null;
        }
    }

    /** Converts image bytes (any format ImageIO can decode) to JPEG bytes. */
    private static byte[] convertToJpeg(byte[] inputBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(inputBytes));
            if (img == null) return null;

            // Ensure no alpha channel — JPEG doesn't support transparency
            if (img.getColorModel().hasAlpha()) {
                BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                rgb.createGraphics().drawImage(img, 0, 0, java.awt.Color.WHITE, null);
                img = rgb;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean success = ImageIO.write(img, "JPEG", out);
            return success ? out.toByteArray() : null;
        } catch (Exception e) {
            System.err.println("OgImageFetcher WebP→JPEG conversion failed: " + e.getMessage());
            return null;
        }
    }

    /** Detects WebP by its file magic bytes: RIFF....WEBP */
    private static boolean isWebP(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 0x52 && bytes[1] == 0x49  // 'RI'
                && bytes[2] == 0x46 && bytes[3] == 0x46  // 'FF'
                && bytes[8] == 0x57 && bytes[9] == 0x45  // 'WE'
                && bytes[10] == 0x42 && bytes[11] == 0x50; // 'BP'
    }
}
