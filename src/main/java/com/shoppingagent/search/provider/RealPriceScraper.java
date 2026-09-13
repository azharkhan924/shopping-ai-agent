package com.shoppingagent.search.provider;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes real product data (direct URLs, actual prices) from Amazon.in and Flipkart
 * search result pages. Falls back gracefully if scraping fails.
 */
@Component
public class RealPriceScraper {

    private static final Logger log = LoggerFactory.getLogger(RealPriceScraper.class);
    private static final int TIMEOUT_MS = 12000;
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    // Pattern to extract price digits from strings like "₹1,04,990" or "₹2,999"
    private static final Pattern PRICE_PATTERN = Pattern.compile("[₹$]?\\s*([\\d,]+)");
    // Pattern to extract ASIN from Amazon URLs
    private static final Pattern ASIN_PATTERN = Pattern.compile("/dp/([A-Z0-9]{10})");

    private final ExecutorService executor = Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "price-scraper"); t.setDaemon(true); return t; });

    /**
     * Represents a scraped product result from a store.
     */
    public record ScrapedProduct(
            String title,
            String directUrl,
            BigDecimal price,
            String store,
            BigDecimal originalPrice
    ) {}

    /**
     * Scrape both Amazon.in and Flipkart in parallel for the given search term.
     * Returns all scraped products combined. Never throws — returns empty list on failure.
     */
    public List<ScrapedProduct> scrapeAll(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) return List.of();

        CompletableFuture<List<ScrapedProduct>> amazonFuture = CompletableFuture
                .supplyAsync(() -> scrapeAmazon(searchTerm), executor);
        CompletableFuture<List<ScrapedProduct>> flipkartFuture = CompletableFuture
                .supplyAsync(() -> scrapeFlipkart(searchTerm), executor);

        try {
            CompletableFuture.allOf(amazonFuture, flipkartFuture).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Scraping timed out or failed for '{}': {}", searchTerm, e.getMessage());
        }

        List<ScrapedProduct> results = new ArrayList<>();
        try { results.addAll(amazonFuture.getNow(List.of())); } catch (Exception ignored) {}
        try { results.addAll(flipkartFuture.getNow(List.of())); } catch (Exception ignored) {}

        log.info("Scraped {} real products for '{}'", results.size(), searchTerm);
        return results;
    }

    /**
     * Scrape Amazon.in search results for real product data.
     */
    public List<ScrapedProduct> scrapeAmazon(String searchTerm) {
        List<ScrapedProduct> results = new ArrayList<>();
        try {
            String url = "https://www.amazon.in/s?k=" + URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
            log.debug("Scraping Amazon.in: {}", url);

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "en-IN,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .referrer("https://www.amazon.in/")
                    .timeout(TIMEOUT_MS)
                    .get();

            // Amazon search results use data-component-type="s-search-result"
            Elements searchResults = doc.select("div[data-component-type=s-search-result]");
            if (searchResults.isEmpty()) {
                searchResults = doc.select("div[data-asin]:not([data-asin=''])");
            }

            for (Element result : searchResults) {
                if (results.size() >= 3) break;

                String asin = result.attr("data-asin");
                if (asin == null || asin.isBlank()) continue;

                if (result.select("span:contains(Sponsored)").size() > 0) continue;

                Element titleEl = result.selectFirst("h2 a span, h2 span.a-text-normal");
                String title = titleEl != null ? titleEl.text().trim() : null;
                if (title == null || title.isBlank()) continue;

                String directUrl = "https://www.amazon.in/dp/" + asin;
                Element linkEl = result.selectFirst("h2 a[href]");
                if (linkEl != null) {
                    String href = linkEl.attr("href");
                    if (href.contains("/dp/")) {
                        directUrl = "https://www.amazon.in" + href.split("\\?")[0];
                        if (!directUrl.contains("/dp/" + asin)) {
                            directUrl = "https://www.amazon.in/dp/" + asin;
                        }
                    }
                }

                Element priceWhole = result.selectFirst("span.a-price:not(.a-text-price) span.a-price-whole");
                BigDecimal price = null;
                if (priceWhole != null) {
                    price = parsePrice(priceWhole.text());
                }
                if (price == null) {
                    Element priceEl = result.selectFirst("span.a-price:not(.a-text-price) span.a-offscreen");
                    if (priceEl != null) {
                        price = parsePrice(priceEl.text());
                    }
                }
                if (price == null) continue;

                BigDecimal originalPrice = null;
                Element origPriceEl = result.selectFirst("span.a-price.a-text-price span.a-offscreen");
                if (origPriceEl != null) {
                    originalPrice = parsePrice(origPriceEl.text());
                }

                results.add(new ScrapedProduct(title, directUrl, price, "Amazon.in", originalPrice));
                log.debug("Amazon scraped: {} → ₹{} → {}", title, price, directUrl);
            }

        } catch (Exception e) {
            log.warn("Failed to scrape Amazon.in for '{}': {}", searchTerm, e.getMessage());
        }
        return results;
    }

    /**
     * Scrape Flipkart search results for real product data.
     * Uses card-based extraction (div[data-id]) to ensure each product gets its
     * OWN distinct title, price, and direct product URL.
     */
    public List<ScrapedProduct> scrapeFlipkart(String searchTerm) {
        List<ScrapedProduct> results = new ArrayList<>();
        try {
            String url = "https://www.flipkart.com/search?q=" + URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
            log.debug("Scraping Flipkart: {}", url);

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "en-IN,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Encoding", "gzip, deflate")
                    .referrer("https://www.flipkart.com/")
                    .timeout(TIMEOUT_MS)
                    .get();

            // Flipkart product cards: each product card is marked with data-id
            Elements cards = doc.select("div[data-id]");
            if (cards.isEmpty()) {
                cards = doc.select("div[class*='cPHDOP'], div[class*='_1AtVbE']");
            }

            java.util.Set<String> seenPids = new java.util.HashSet<>();

            for (Element card : cards) {
                if (results.size() >= 5) break;

                String dataId = card.attr("data-id");
                if (dataId != null && !dataId.isBlank()) {
                    if (seenPids.contains(dataId)) continue;
                    seenPids.add(dataId);
                }

                // Direct product page link
                Element link = card.selectFirst("a[href*='/p/']");
                if (link == null) continue;

                String href = link.attr("href");
                String directUrl = href.startsWith("http") ? href : "https://www.flipkart.com" + href;

                // Title: Flipkart provides clean title in a[title], img[alt], or specific div
                String title = null;
                Element titleLink = card.selectFirst("a[title]");
                if (titleLink != null && !titleLink.attr("title").isBlank()) {
                    title = titleLink.attr("title").trim();
                }
                if (title == null || title.isBlank()) {
                    Element img = card.selectFirst("img[alt]");
                    if (img != null && !img.attr("alt").isBlank()) {
                        title = img.attr("alt").trim();
                    }
                }
                if (title == null || title.isBlank()) {
                    Element titleDiv = card.selectFirst("div[class*='KzDlHZ'], div[class*='_4rR01T'], a[class*='IRpwTa'], a.s1Q9rs, div[class*='WKTcLC']");
                    if (titleDiv != null) {
                        title = titleDiv.text().trim();
                    }
                }
                if (title == null || title.length() < 3) continue;

                // Price: Look strictly inside THIS card
                BigDecimal price = null;
                BigDecimal originalPrice = null;

                Element priceEl = card.selectFirst("div[class*='Nx9bqj'], div[class*='_30jeq3'], div[class*='hZ3P6w']");
                if (priceEl != null) {
                    price = parsePrice(priceEl.text());
                }

                // Fallback: look for any element starting with ₹ within this card
                if (price == null) {
                    for (Element el : card.select("div, span")) {
                        String text = el.ownText().trim();
                        if (text.startsWith("₹")) {
                            BigDecimal parsed = parsePrice(text);
                            if (parsed != null) {
                                price = parsed;
                                break;
                            }
                        }
                    }
                }

                if (price == null) continue; // Skip if no real price

                // MRP (strikethrough) strictly inside this card
                Element mrpEl = card.selectFirst("div[class*='yRaY8j'], div[class*='_3I9_wc'], div[class*='kRYCnD']");
                if (mrpEl != null) {
                    BigDecimal parsed = parsePrice(mrpEl.text());
                    if (parsed != null && parsed.compareTo(price) > 0) {
                        originalPrice = parsed;
                    }
                }

                results.add(new ScrapedProduct(title, directUrl, price, "Flipkart", originalPrice));
                log.debug("Flipkart scraped: {} → ₹{} → {}", title, price, directUrl);
            }

        } catch (Exception e) {
            log.warn("Failed to scrape Flipkart for '{}': {}", searchTerm, e.getMessage());
        }
        return results;
    }

    /**
     * Parse a price string like "₹1,04,990" or "1,299" or "₹ 69,990" into a BigDecimal.
     */
    private static BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) return null;
        try {
            // Remove everything except digits
            String digits = priceStr.replaceAll("[^\\d]", "");
            if (digits.isBlank() || digits.length() > 10) return null;
            BigDecimal val = new BigDecimal(digits);
            // Sanity check: price should be > ₹10 and < ₹50,00,000
            if (val.compareTo(BigDecimal.TEN) < 0 || val.compareTo(new BigDecimal("5000000")) > 0) return null;
            return val;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
