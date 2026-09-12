package com.shoppingagent.product.ranking;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.product.grouping.ProductGroup;
import com.shoppingagent.search.model.Product;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Module 7.1 — Scores and ranks product groups based on weighted criteria.
 * All ranking is deterministic — the LLM is never involved in scoring.
 */
@Service
public class ProductRankingService {

    // Default weights (sum to 1.0)
    private static final double W_PRICE = 0.30;
    private static final double W_RATING = 0.25;
    private static final double W_REVIEWS = 0.15;
    private static final double W_SPECS = 0.15;
    private static final double W_VALUE = 0.15;

    /**
     * Ranks product groups and assigns final scores.
     * Returns a new list sorted by finalScore descending.
     */
    public List<ProductGroup> rank(List<ProductGroup> groups, ShoppingQuery query) {
        if (groups == null || groups.isEmpty()) return List.of();

        // Compute min/max for normalization
        double maxPrice = groups.stream()
                .filter(g -> g.getBestPrice() != null)
                .mapToDouble(ProductGroup::getBestPrice)
                .max().orElse(1.0);
        double minPrice = groups.stream()
                .filter(g -> g.getBestPrice() != null)
                .mapToDouble(ProductGroup::getBestPrice)
                .min().orElse(0.0);

        double maxRating = groups.stream()
                .filter(g -> g.getRating() != null)
                .mapToDouble(ProductGroup::getRating)
                .max().orElse(5.0);

        int maxReviews = groups.stream()
                .filter(g -> g.getReviewCount() != null)
                .mapToInt(ProductGroup::getReviewCount)
                .max().orElse(1);

        for (ProductGroup group : groups) {
            double priceScore = computePriceScore(group, query, minPrice, maxPrice);
            double ratingScore = computeRatingScore(group, maxRating);
            double reviewScore = computeReviewScore(group, maxReviews);
            double specsScore = computeSpecsMatchScore(group, query);
            double valueScore = computeValueScore(priceScore, ratingScore);

            double finalScore = (priceScore * W_PRICE)
                    + (ratingScore * W_RATING)
                    + (reviewScore * W_REVIEWS)
                    + (specsScore * W_SPECS)
                    + (valueScore * W_VALUE);

            group.setFinalScore(Math.round(finalScore * 100.0) / 100.0);
        }

        return groups.stream()
                .sorted(Comparator.comparingDouble(ProductGroup::getFinalScore).reversed())
                .toList();
    }

    private double computePriceScore(ProductGroup group, ShoppingQuery query, double minPrice, double maxPrice) {
        if (group.getBestPrice() == null) return 0.5;

        double price = group.getBestPrice();

        // Budget fit: if within budget, full score; if over, penalty
        if (query != null && query.getMaxPrice() != null) {
            double budget = query.getMaxPrice();
            if (price <= budget) {
                // How far under budget (normalized)
                return 0.7 + (0.3 * (1.0 - price / budget));
            } else {
                return Math.max(0, 0.5 * (1.0 - (price - budget) / budget));
            }
        }

        // No budget: normalize inversely (cheaper = higher score)
        if (maxPrice == minPrice) return 0.5;
        return 1.0 - ((price - minPrice) / (maxPrice - minPrice));
    }

    private double computeRatingScore(ProductGroup group, double maxRating) {
        if (group.getRating() == null) return 0.5;
        return group.getRating() / maxRating;
    }

    private double computeReviewScore(ProductGroup group, int maxReviews) {
        if (group.getReviewCount() == null || maxReviews == 0) return 0.3;
        return Math.min(1.0, (double) group.getReviewCount() / maxReviews);
    }

    private double computeSpecsMatchScore(ProductGroup group, ShoppingQuery query) {
        if (query == null) return 0.5;
        Product rep = group.getRepresentativeProduct();
        if (rep == null || rep.getSpecifications() == null) return 0.5;

        Map<String, String> required = query.getRequiredSpecifications();
        if (required == null || required.isEmpty()) return 0.7; // No specific req = decent match

        int matched = 0;
        int total = required.size();

        for (Map.Entry<String, String> entry : required.entrySet()) {
            String specValue = rep.getSpecifications().get(entry.getKey());
            if (specValue != null && specValue.equalsIgnoreCase(entry.getValue())) {
                matched++;
            }
        }

        return total > 0 ? (double) matched / total : 0.5;
    }

    private double computeValueScore(double priceScore, double ratingScore) {
        // Value = good rating at a good price
        return (priceScore + ratingScore) / 2.0;
    }
}
