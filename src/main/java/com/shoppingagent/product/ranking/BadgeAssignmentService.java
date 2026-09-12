package com.shoppingagent.product.ranking;

import com.shoppingagent.product.grouping.ProductGroup;
import com.shoppingagent.search.model.Product;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Module 7.2 — Assigns recommendation badges to top products deterministically.
 * Badges: BEST_OVERALL (rank 1), CHEAPEST (lowest price), BEST_VALUE (best score/price ratio),
 * HIGHEST_RATED, MOST_POPULAR (most reviews), BEST_BATTERY (if applicable).
 */
@Service
public class BadgeAssignmentService {

    /**
     * Assigns badges to the top product groups. Groups must already be ranked.
     * Mutates the groups in place (sets badge + reason).
     */
    public void assignBadges(List<ProductGroup> rankedGroups) {
        if (rankedGroups == null || rankedGroups.isEmpty()) return;

        // BEST_OVERALL = rank 1
        ProductGroup best = rankedGroups.get(0);
        if (best.getBadge() == null) {
            best.setBadge(RecommendationBadge.BEST_OVERALL.name());
            best.setReason("Top overall score based on price, rating, and value.");
        }

        // CHEAPEST = lowest bestPrice
        rankedGroups.stream()
                .filter(g -> g.getBestPrice() != null && g.getBadge() == null)
                .min(Comparator.comparingDouble(ProductGroup::getBestPrice))
                .ifPresent(g -> {
                    g.setBadge(RecommendationBadge.CHEAPEST.name());
                    g.setReason("Lowest price among all matching products.");
                });

        // HIGHEST_RATED = best rating
        rankedGroups.stream()
                .filter(g -> g.getRating() != null && g.getBadge() == null)
                .max(Comparator.comparingDouble(ProductGroup::getRating))
                .ifPresent(g -> {
                    g.setBadge(RecommendationBadge.HIGHEST_RATED.name());
                    g.setReason("Highest customer rating in this category.");
                });

        // BEST_VALUE = best finalScore/price ratio (exclude already badged)
        rankedGroups.stream()
                .filter(g -> g.getBestPrice() != null && g.getBestPrice() > 0 && g.getBadge() == null)
                .max(Comparator.comparingDouble(g -> g.getFinalScore() / g.getBestPrice()))
                .ifPresent(g -> {
                    g.setBadge(RecommendationBadge.BEST_VALUE.name());
                    g.setReason("Best combination of quality and price.");
                });

        // MOST_POPULAR = most reviews
        rankedGroups.stream()
                .filter(g -> g.getReviewCount() != null && g.getBadge() == null)
                .max(Comparator.comparingInt(ProductGroup::getReviewCount))
                .ifPresent(g -> {
                    g.setBadge(RecommendationBadge.MOST_POPULAR.name());
                    g.setReason("Most reviewed and popular choice.");
                });

        // BEST_BATTERY = for headphones/watches, if battery spec is available
        rankedGroups.stream()
                .filter(g -> g.getBadge() == null && hasBatterySpec(g))
                .max(Comparator.comparingInt(this::extractBatteryHours))
                .ifPresent(g -> {
                    g.setBadge(RecommendationBadge.BEST_BATTERY.name());
                    g.setReason("Longest battery life in this selection.");
                });
    }

    private boolean hasBatterySpec(ProductGroup group) {
        Product rep = group.getRepresentativeProduct();
        if (rep == null || rep.getSpecifications() == null) return false;
        String battery = rep.getSpecifications().get("batteryLife");
        return battery != null && !battery.isBlank() && !"N/A".equalsIgnoreCase(battery);
    }

    private int extractBatteryHours(ProductGroup group) {
        Product rep = group.getRepresentativeProduct();
        if (rep == null || rep.getSpecifications() == null) return 0;
        String battery = rep.getSpecifications().get("batteryLife");
        if (battery == null) return 0;
        try {
            return Integer.parseInt(battery.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
