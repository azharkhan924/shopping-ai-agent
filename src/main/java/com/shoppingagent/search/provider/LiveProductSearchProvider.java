package com.shoppingagent.search.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.model.Availability;
import com.shoppingagent.search.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Module 5 Live Provider — Queries the AI Shopping Brain to identify real-world
 * products, then SCRAPES actual Amazon.in and Flipkart pages to get:
 *   1. Direct product page URLs (not search pages)
 *   2. Real, current market prices (not LLM-hallucinated approximations)
 *
 * Falls back to search-page URLs only when scraping fails.
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "shopping.search", name = "mode", havingValue = "live", matchIfMissing = true)
public class LiveProductSearchProvider implements ProductSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(LiveProductSearchProvider.class);
    private static final String SOURCE = "LIVE_AI";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final RealPriceScraper realPriceScraper;
    private final MockProductSearchProvider fallback = new MockProductSearchProvider();

    public LiveProductSearchProvider(ChatClient shoppingChatClient, ObjectMapper objectMapper,
                                      RealPriceScraper realPriceScraper) {
        this.chatClient = shoppingChatClient;
        this.objectMapper = objectMapper;
        this.realPriceScraper = realPriceScraper;
    }

    @Override
    public List<Product> search(ShoppingQuery query) {
        try {
            List<Product> liveProducts = fetchLiveProducts(query);
            if (liveProducts != null && !liveProducts.isEmpty()) {
                log.info("LiveProductSearchProvider found {} real products for query", liveProducts.size());
                return liveProducts;
            }
        } catch (Exception e) {
            log.warn("LiveProductSearchProvider AI query failed, falling back to mock catalog: {}", e.getMessage());
        }

        // Resilient fallback
        return fallback.search(query);
    }

    private List<Product> fetchLiveProducts(ShoppingQuery query) throws Exception {
        String systemPrompt = """
                You are Bachat AI's product identification engine for the Indian market.
                Your job is to identify 3 to 5 REAL, GENUINE products currently sold on Amazon.in and Flipkart.

                CRITICAL INSTRUCTIONS:
                1. Product Name: Give the concise, real official product name (e.g. "Apple MacBook Air M3", "Sony WH-1000XM5", "Pigeon Healthifry 4.2L Air Fryer").
                2. Search Keyword: In "searchKeyword", provide a PRECISE search term (2-5 words) that directly identifies THIS EXACT PRODUCT on Amazon.in and Flipkart.
                   Examples: "MacBook Air M3 16GB", "Sony WH-1000XM5", "Pigeon Healthifry Air Fryer", "Philips BT3211 Trimmer"
                3. Any Category: Support ANY consumer product: electronics, smartphones, laptops, clothing, shoes, kitchen, fitness, personal care, home, etc.
                4. Price: Give your BEST ESTIMATE of the current Indian retail price in INR. This is ONLY used as a fallback — we will scrape actual prices.

                Return ONLY a valid JSON array. No markdown, no backticks. Raw JSON array only.

                JSON Object schema:
                {
                  "name": "Concise official model name",
                  "brand": "Official brand name",
                  "category": "General category",
                  "searchKeyword": "Precise 2-5 word search term for exact store match",
                  "description": "Short 1-sentence product highlight",
                  "price": 35990,
                  "originalPrice": 49990,
                  "rating": 4.4,
                  "reviewCount": 4200,
                  "store": "Amazon.in",
                  "specifications": { "key": "value" }
                }
                Ensure prices strictly respect any budget specified by the user.
                """;

        StringBuilder userPrompt = new StringBuilder();
        String cat = query.getCategory() != null ? query.getCategory() : "products";
        userPrompt.append("Category/Item: ").append(cat);

        if (query.getKeywords() != null && !query.getKeywords().isEmpty()) {
            userPrompt.append(", Keywords: ").append(String.join(", ", query.getKeywords()));
        }
        if (query.getMaxPrice() != null) {
            userPrompt.append(", Maximum budget: ₹").append(Math.round(query.getMaxPrice()));
        }
        if (query.getMinPrice() != null) {
            userPrompt.append(", Minimum price: ₹").append(Math.round(query.getMinPrice()));
        }
        if (query.getBrands() != null && !query.getBrands().isEmpty()) {
            userPrompt.append(", Preferred brands: ").append(String.join(", ", query.getBrands()));
        }
        if (query.getRequiredSpecifications() != null && !query.getRequiredSpecifications().isEmpty()) {
            userPrompt.append(", Required specifications: ").append(query.getRequiredSpecifications());
        }

        String rawResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt.toString())
                .call()
                .content();

        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }

        String cleaned = cleanJson(rawResponse);
        JsonNode root = objectMapper.readTree(cleaned);

        if (!root.isArray()) {
            return List.of();
        }

        List<Product> products = new ArrayList<>();
        int index = 1;

        for (JsonNode node : root) {
            String name = node.path("name").asText("").trim();
            if (name.isBlank()) continue;

            String brand = node.path("brand").asText("Generic").trim();
            String category = node.path("category").asText(cat).trim();
            String desc = node.path("description").asText(name);
            String searchKeyword = node.path("searchKeyword").asText("").trim();
            double llmPriceVal = node.path("price").asDouble(0.0);
            if (llmPriceVal <= 0) continue;

            double llmOrigPriceVal = node.path("originalPrice").asDouble(llmPriceVal * 1.25);
            if (llmOrigPriceVal <= llmPriceVal) {
                llmOrigPriceVal = Math.round(llmPriceVal * 1.28);
            }
            double rating = node.path("rating").asDouble(4.3);
            int reviews = node.path("reviewCount").asInt(1500);
            String store = node.path("store").asText(index % 2 == 0 ? "Flipkart" : "Amazon.in");

            Map<String, String> specs = new LinkedHashMap<>();
            JsonNode specsNode = node.path("specifications");
            if (specsNode.isObject()) {
                specsNode.fields().forEachRemaining(entry -> specs.put(entry.getKey(), entry.getValue().asText()));
            }

            // Build clean search term for scraping
            String effectiveSearchTerm = !searchKeyword.isBlank()
                    ? cleanSearchTerm(brand, searchKeyword)
                    : cleanSearchTerm(brand, name);

            // ─── SCRAPE REAL DATA ───────────────────────────────────────────
            // Scrape real prices and direct product URLs from Amazon.in & Flipkart
            List<RealPriceScraper.ScrapedProduct> scraped = realPriceScraper.scrapeAll(effectiveSearchTerm);

            String imageUrl = resolveImageUrl(category, name);

            if (!scraped.isEmpty()) {
                // Use SCRAPED data — real prices, real direct URLs
                for (RealPriceScraper.ScrapedProduct sp : scraped) {
                    BigDecimal realPrice = sp.price();
                    BigDecimal realOrigPrice = sp.originalPrice() != null ? sp.originalPrice()
                            : realPrice.multiply(BigDecimal.valueOf(1.15)).setScale(0, java.math.RoundingMode.HALF_UP);

                    double realPriceD = realPrice.doubleValue();
                    double realOrigD = realOrigPrice.doubleValue();

                    Product product = Product.builder()
                            .id("real-" + UUID.randomUUID().toString().substring(0, 8))
                            .name(sp.title() != null && sp.title().length() > 5 ? sp.title() : name)
                            .brand(brand)
                            .category(category.toLowerCase(Locale.ROOT))
                            .description(desc)
                            .imageUrl(imageUrl)
                            .price(realPrice)
                            .originalPrice(realOrigPrice)
                            .discountPercentage(calculateDiscount(realPriceD, realOrigD))
                            .currency("INR")
                            .rating(rating)
                            .reviewCount(reviews)
                            .store(sp.store())
                            .productUrl(sp.directUrl())   // ← DIRECT PRODUCT PAGE URL
                            .availability(Availability.IN_STOCK)
                            .deliveryInfo("1-2 business days")
                            .specifications(specs)
                            .source(SOURCE + "_VERIFIED")
                            .lastCheckedAt(Instant.now())
                            .build();

                    products.add(product);
                }
                log.info("✓ Scraped {} REAL offers for '{}' (search: '{}')", scraped.size(), name, effectiveSearchTerm);
            } else {
                // FALLBACK: scraping failed — use LLM price + search URL (old behavior)
                log.warn("✗ Scraping failed for '{}' — using LLM estimates with search URLs", name);
                String productUrl = buildStoreSearchUrl(store, effectiveSearchTerm);

                Product product = Product.builder()
                        .id("real-" + UUID.randomUUID().toString().substring(0, 8))
                        .name(name)
                        .brand(brand)
                        .category(category.toLowerCase(Locale.ROOT))
                        .description(desc)
                        .imageUrl(imageUrl)
                        .price(BigDecimal.valueOf(llmPriceVal))
                        .originalPrice(BigDecimal.valueOf(llmOrigPriceVal))
                        .discountPercentage(calculateDiscount(llmPriceVal, llmOrigPriceVal))
                        .currency("INR")
                        .rating(rating)
                        .reviewCount(reviews)
                        .store(store)
                        .productUrl(productUrl)
                        .availability(Availability.IN_STOCK)
                        .deliveryInfo("1-2 business days")
                        .specifications(specs)
                        .source(SOURCE + "_ESTIMATED")
                        .lastCheckedAt(Instant.now())
                        .build();

                products.add(product);
            }
            index++;
        }

        // Also perform direct scrape of the primary search query if available
        List<String> queryParts = new ArrayList<>();
        if (query.getBrands() != null) queryParts.addAll(query.getBrands());
        if (query.getKeywords() != null) queryParts.addAll(query.getKeywords());
        String directQueryTerm = String.join(" ", queryParts).trim();
        if (!directQueryTerm.isBlank() && directQueryTerm.length() > 3) {
            List<RealPriceScraper.ScrapedProduct> directScraped = realPriceScraper.scrapeAll(directQueryTerm);
            for (RealPriceScraper.ScrapedProduct sp : directScraped) {
                BigDecimal realPrice = sp.price();
                BigDecimal realOrigPrice = sp.originalPrice() != null ? sp.originalPrice()
                        : realPrice.multiply(BigDecimal.valueOf(1.15)).setScale(0, java.math.RoundingMode.HALF_UP);
                double realPriceD = realPrice.doubleValue();
                double realOrigD = realOrigPrice.doubleValue();

                products.add(Product.builder()
                        .id("real-" + UUID.randomUUID().toString().substring(0, 8))
                        .name(sp.title() != null ? sp.title() : directQueryTerm)
                        .brand(!query.getBrands().isEmpty() ? query.getBrands().get(0) : "Generic")
                        .category(cat.toLowerCase(Locale.ROOT))
                        .description(sp.title() != null ? sp.title() : directQueryTerm)
                        .imageUrl(resolveImageUrl(cat, sp.title()))
                        .price(realPrice)
                        .originalPrice(realOrigPrice)
                        .discountPercentage(calculateDiscount(realPriceD, realOrigD))
                        .currency("INR")
                        .rating(4.5)
                        .reviewCount(2500)
                        .store(sp.store())
                        .productUrl(sp.directUrl())
                        .availability(Availability.IN_STOCK)
                        .deliveryInfo("1-2 business days")
                        .source(SOURCE + "_VERIFIED")
                        .lastCheckedAt(Instant.now())
                        .build());
            }
        }

        // If we found ANY verified real products, drop ALL unverified fallback products
        // to prevent LLM hallucinated prices from polluting the comparison
        boolean hasVerified = products.stream()
                .anyMatch(p -> p.getSource() != null && p.getSource().endsWith("_VERIFIED"));
        if (hasVerified) {
            List<Product> verifiedOnly = products.stream()
                    .filter(p -> p.getSource() != null && p.getSource().endsWith("_VERIFIED"))
                    .toList();
            log.info("LiveProductSearchProvider: Returning {} VERIFIED products (dropped {} unverified fallbacks)",
                    verifiedOnly.size(), products.size() - verifiedOnly.size());
            return verifiedOnly;
        }

        return products;
    }

    private String cleanJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastBackticks = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastBackticks).trim();
            }
        }
        return trimmed;
    }

    private Double calculateDiscount(double price, double originalPrice) {
        if (originalPrice <= price || originalPrice <= 0) return null;
        return Math.round(((originalPrice - price) / originalPrice) * 1000.0) / 10.0;
    }

    public static String cleanSearchTerm(String brand, String productName) {
        if (productName == null || productName.isBlank()) {
            return brand != null ? brand.trim() : "";
        }
        // Remove text in parentheses: (13.6-inch, 8GB RAM, 256GB SSD) -> ""
        String clean = productName.replaceAll("\\([^)]*\\)", " ");
        // Remove text in brackets: [2024 Edition] -> ""
        clean = clean.replaceAll("\\[[^]]*\\]", " ");
        // Remove special characters that break Amazon/Flipkart search algorithms
        clean = clean.replaceAll("[\",:;\\\\/|~#*+?]", " ");
        // Remove screen dimension and OS noise
        clean = clean.replaceAll("(?i)\\b\\d+(\\.\\d+)?\\s*(inch|\"|cm|mm)\\b", " ");
        clean = clean.replaceAll("(?i)\\b(windows|ubuntu|dos|android|ios)\\s*\\d*\\b", " ");
        // Normalize whitespace
        clean = clean.replaceAll("\\s+", " ").trim();

        // Ensure brand name is present if not already contained
        if (brand != null && !brand.isBlank() && !brand.equalsIgnoreCase("Generic")) {
            String bLower = brand.toLowerCase(Locale.ROOT).trim();
            if (!clean.toLowerCase(Locale.ROOT).contains(bLower)) {
                clean = brand.trim() + " " + clean;
            }
        }

        // Limit to first 5-6 meaningful words for precise scraping
        String[] words = clean.split("\\s+");
        if (words.length > 6) {
            clean = String.join(" ", java.util.Arrays.copyOfRange(words, 0, 6));
        }
        return clean.trim();
    }

    public static String buildStoreSearchUrl(String store, String searchTerm) {
        String encoded = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String s = store == null ? "" : store.toLowerCase(Locale.ROOT);

        if (s.contains("flipkart")) {
            return "https://www.flipkart.com/search?q=" + encoded;
        } else if (s.contains("croma")) {
            return "https://www.croma.com/searchB?q=" + encoded;
        } else if (s.contains("reliance")) {
            return "https://www.reliancedigital.in/search?q=" + encoded;
        }
        return "https://www.amazon.in/s?k=" + encoded;
    }

    public static String resolveImageUrl(String category, String name) {
        String combined = ((category == null ? "" : category) + " " + (name == null ? "" : name)).toLowerCase(Locale.ROOT);

        if (combined.contains("laptop") || combined.contains("notebook") || combined.contains("macbook")) {
            return "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("smartwatch") || combined.contains("watch") || combined.contains("band")) {
            return "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("headphone") || combined.contains("earphone") || combined.contains("earbud") || combined.contains("airpod") || combined.contains("tws")) {
            return "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("phone") || combined.contains("smartphone") || combined.contains("iphone") || combined.contains("samsung")) {
            return "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("shoe") || combined.contains("sneaker") || combined.contains("running") || combined.contains("puma") || combined.contains("nike")) {
            return "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("fryer") || combined.contains("airfryer") || combined.contains("kitchen") || combined.contains("mixer") || combined.contains("cooker")) {
            return "https://images.unsplash.com/photo-1585515320310-259814833e62?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("trimmer") || combined.contains("shaver") || combined.contains("groom") || combined.contains("dryer")) {
            return "https://images.unsplash.com/photo-1621607512214-68297480165e?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("mouse")) {
            return "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("keyboard")) {
            return "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("pendrive") || combined.contains("usb") || combined.contains("flash drive") || combined.contains("storage")) {
            return "https://images.unsplash.com/photo-1624823183493-5f6b25191b49?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("tablet") || combined.contains("ipad")) {
            return "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("camera") || combined.contains("dslr")) {
            return "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("monitor") || combined.contains("screen") || combined.contains("tv") || combined.contains("television")) {
            return "https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("bag") || combined.contains("backpack")) {
            return "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=600&q=80";
        }
        if (combined.contains("jacket") || combined.contains("shirt") || combined.contains("hoodie") || combined.contains("cloth") || combined.contains("jean")) {
            return "https://images.unsplash.com/photo-1551028719-00167b16eac5?auto=format&fit=crop&w=600&q=80";
        }
        return "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80";
    }

    @Override
    public String getProviderName() {
        return SOURCE;
    }
}
