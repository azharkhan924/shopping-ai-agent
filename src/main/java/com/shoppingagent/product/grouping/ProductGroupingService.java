package com.shoppingagent.product.grouping;

import com.shoppingagent.product.dedup.DuplicateDetectionService;
import com.shoppingagent.search.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Module 6.3 — Groups duplicate products (same item, different stores) into ProductGroups.
 * Each group has a representative product and a list of offers.
 */
@Service
public class ProductGroupingService {

    private final DuplicateDetectionService duplicateDetectionService;

    public ProductGroupingService(DuplicateDetectionService duplicateDetectionService) {
        this.duplicateDetectionService = duplicateDetectionService;
    }

    /**
     * Takes a flat list of products and groups duplicates together.
     * Returns ProductGroups sorted by best price ascending.
     */
    public List<ProductGroup> group(List<Product> products) {
        if (products == null || products.isEmpty()) return List.of();

        List<List<Product>> duplicateGroups = duplicateDetectionService.detectDuplicateGroups(products);
        List<ProductGroup> result = new ArrayList<>();

        for (List<Product> dupGroup : duplicateGroups) {
            result.add(buildGroup(dupGroup));
        }

        return result;
    }

    private ProductGroup buildGroup(List<Product> products) {
        // Pick the "best" product as representative (highest rating, then most reviews)
        Product representative = products.stream()
                .max(Comparator.comparingDouble((Product p) -> p.getRating() != null ? p.getRating() : 0.0)
                        .thenComparingInt(p -> p.getReviewCount() != null ? p.getReviewCount() : 0))
                .orElse(products.get(0));

        // Keep only the lowest price offer per unique store
        Map<String, ProductOffer> storeMap = new LinkedHashMap<>();
        for (Product p : products) {
            ProductOffer offer = toOffer(p);
            String storeKey = offer.getStore() != null ? offer.getStore().toLowerCase(Locale.ROOT) : "unknown";
            ProductOffer existing = storeMap.get(storeKey);
            if (existing == null || (offer.getPrice() != null && (existing.getPrice() == null || offer.getPrice().compareTo(existing.getPrice()) < 0))) {
                storeMap.put(storeKey, offer);
            }
        }
        List<ProductOffer> offers = new ArrayList<>(storeMap.values());
        offers.sort(Comparator.comparing(o -> o.getPrice() != null ? o.getPrice() : BigDecimal.valueOf(Double.MAX_VALUE)));

        if (offers.size() == 1 && representative.getName() != null) {
            // Only add a "check price" link to the other store — NO fabricated price
            ProductOffer mainOffer = offers.get(0);
            boolean isAmazon = mainOffer.getStore() != null && mainOffer.getStore().toLowerCase().contains("amazon");
            String compStore = isAmazon ? "Flipkart" : "Amazon.in";
            String compUrl = isAmazon
                    ? "https://www.flipkart.com/search?q=" + java.net.URLEncoder.encode(representative.getName(), java.nio.charset.StandardCharsets.UTF_8)
                    : "https://www.amazon.in/s?k=" + java.net.URLEncoder.encode(representative.getName(), java.nio.charset.StandardCharsets.UTF_8);

            ProductOffer compOffer = ProductOffer.builder()
                    .store(compStore)
                    .price(null)  // Don't fabricate a price — let the user check
                    .currency(mainOffer.getCurrency())
                    .productUrl(compUrl)
                    .availability("CHECK_STORE")
                    .deliveryInfo("Check store for details")
                    .build();

            List<ProductOffer> list = new ArrayList<>(offers);
            list.add(compOffer);
            offers = list;
        }

        Double bestPrice = offers.stream()
                .map(ProductOffer::getPrice)
                .filter(p -> p != null)
                .map(BigDecimal::doubleValue)
                .min(Double::compare)
                .orElse(null);

        return ProductGroup.builder()
                .groupId("g-" + UUID.randomUUID().toString().substring(0, 8))
                .name(representative.getName())
                .brand(representative.getBrand())
                .category(representative.getCategory())
                .description(representative.getDescription())
                .imageUrl(representative.getImageUrl())
                .rating(representative.getRating())
                .reviewCount(aggregateReviewCount(products))
                .offers(offers)
                .bestPrice(bestPrice)
                .currency(representative.getCurrency())
                .representativeProduct(representative)
                .build();
    }

    private ProductOffer toOffer(Product product) {
        return ProductOffer.builder()
                .store(product.getStore())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .productUrl(product.getProductUrl())
                .availability(product.getAvailability() != null ? product.getAvailability().name() : null)
                .deliveryInfo(product.getDeliveryInfo())
                .originalPrice(product.getOriginalPrice())
                .discountPercentage(product.getDiscountPercentage())
                .build();
    }

    private Integer aggregateReviewCount(List<Product> products) {
        int total = 0;
        boolean hasAny = false;
        for (Product p : products) {
            if (p.getReviewCount() != null) {
                total += p.getReviewCount();
                hasAny = true;
            }
        }
        return hasAny ? total : null;
    }
}
