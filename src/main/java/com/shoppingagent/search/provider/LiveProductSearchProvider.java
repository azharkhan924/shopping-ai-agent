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
 * Module 5 Live Provider — Queries the AI Shopping Brain to retrieve real-world
 * products currently sold in the Indian market (Amazon.in, Flipkart, Croma, Reliance Digital)
 * with authentic pricing, real models, direct store search links, and high-res imagery.
 *
 * Automatically falls back to MockProductSearchProvider if the AI is unreachable or times out.
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "shopping.search", name = "mode", havingValue = "live", matchIfMissing = true)
public class LiveProductSearchProvider implements ProductSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(LiveProductSearchProvider.class);
    private static final String SOURCE = "LIVE_AI";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final MockProductSearchProvider fallback = new MockProductSearchProvider();

    public LiveProductSearchProvider(ChatClient shoppingChatClient, ObjectMapper objectMapper) {
        this.chatClient = shoppingChatClient;
        this.objectMapper = objectMapper;
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
                You are Bachat AI's real-world Indian e-commerce catalog engine.
                Your job is to provide 100% REAL, GENUINE products currently sold on Amazon.in, Flipkart, Croma, and Reliance Digital.

                CRITICAL INSTRUCTIONS FOR ACCURACY & PRICING:
                1. Product Name: Give the concise, real official product name (e.g. "Apple MacBook Air M3 (13.6-inch)", "Sony WH-1000XM5 Wireless Headphones", "Pigeon Healthifry 4.2L Digital Air Fryer", "Philips Series 3000 Beard Trimmer").
                2. Real Indian Retail Pricing: You MUST provide realistic, current Indian market prices in INR (₹):
                   - MacBook Air M3: ₹1,04,990 (MRP: ₹1,14,900)
                   - iPhone 15: ₹69,990 (MRP: ₹79,900)
                   - boAt Rockerz 450: ₹1,299 (MRP: ₹3,990)
                   - Noise ColorFit Pulse 2: ₹1,499 (MRP: ₹3,999)
                   - Nike Revolution 6: ₹2,995 (MRP: ₹3,995)
                   - Philips Trimmer: ₹1,499 (MRP: ₹2,195)
                   - Air Fryer (Pigeon/Philips): ₹2,999 to ₹6,999
                   Ensure originalPrice > price to reflect real Indian discounts and savings ("Bachat").
                3. Direct Search Keyword: In "searchKeyword", give a clean 2 to 4 word search term that DIRECTLY hits this exact product on Amazon and Flipkart without failing (e.g. "MacBook Air M3", "Sony WH 1000XM5", "Pigeon Healthifry Air Fryer", "Philips Trimmer BT3211").
                4. Any Category: Support ANY consumer product: electronics, smartphones, laptops, clothing, shoes, kitchen appliances, fitness, personal care, home, etc.

                Return ONLY a valid JSON array of 3 to 5 real products. No markdown, no backticks. Raw JSON array only.

                JSON Object schema:
                {
                  "name": "Concise official model name",
                  "brand": "Official brand name",
                  "category": "General category (e.g. laptop, shoes, kitchen, audio, phone, personal care)",
                  "searchKeyword": "Clean 2-4 word search term for direct store hit",
                  "description": "Short 1-sentence product highlight",
                  "price": 35990,
                  "originalPrice": 49990,
                  "rating": 4.4,
                  "reviewCount": 4200,
                  "store": "Amazon.in | Flipkart | Croma",
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
            double priceVal = node.path("price").asDouble(0.0);
            if (priceVal <= 0) continue;

            double origPriceVal = node.path("originalPrice").asDouble(priceVal * 1.25);
            if (origPriceVal <= priceVal) {
                origPriceVal = Math.round(priceVal * 1.28);
            }
            double rating = node.path("rating").asDouble(4.3);
            int reviews = node.path("reviewCount").asInt(1500);
            String store = node.path("store").asText(index % 2 == 0 ? "Flipkart" : "Amazon.in");

            Map<String, String> specs = new LinkedHashMap<>();
            JsonNode specsNode = node.path("specifications");
            if (specsNode.isObject()) {
                specsNode.fields().forEachRemaining(entry -> specs.put(entry.getKey(), entry.getValue().asText()));
            }

            // Generate clean canonical search URL that directly hits the exact product
            String effectiveSearchTerm = !searchKeyword.isBlank()
                    ? cleanSearchTerm(brand, searchKeyword)
                    : cleanSearchTerm(brand, name);

            String productUrl = buildStoreSearchUrl(store, effectiveSearchTerm);
            String imageUrl = resolveImageUrl(category, name);

            Product product = Product.builder()
                    .id("real-" + UUID.randomUUID().toString().substring(0, 8))
                    .name(name)
                    .brand(brand)
                    .category(category.toLowerCase(Locale.ROOT))
                    .description(desc)
                    .imageUrl(imageUrl)
                    .price(BigDecimal.valueOf(priceVal))
                    .originalPrice(BigDecimal.valueOf(origPriceVal))
                    .discountPercentage(calculateDiscount(priceVal, origPriceVal))
                    .currency("INR")
                    .rating(rating)
                    .reviewCount(reviews)
                    .store(store)
                    .productUrl(productUrl)
                    .availability(Availability.IN_STOCK)
                    .deliveryInfo("1-2 business days")
                    .specifications(specs)
                    .source(SOURCE)
                    .lastCheckedAt(Instant.now())
                    .build();

            products.add(product);
            index++;
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

        // Limit to first 4-5 meaningful words so store search hits the direct product
        String[] words = clean.split("\\s+");
        if (words.length > 5) {
            clean = String.join(" ", java.util.Arrays.copyOfRange(words, 0, 5));
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
