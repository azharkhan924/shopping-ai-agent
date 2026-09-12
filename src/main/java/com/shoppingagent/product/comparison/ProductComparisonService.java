package com.shoppingagent.product.comparison;

import com.shoppingagent.product.grouping.ProductGroup;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Module 7.3 — Compares products side-by-side on key dimensions.
 */
@Service
public class ProductComparisonService {

    /**
     * Compare a list of product groups and return structured comparison data.
     */
    public ComparisonResult compare(List<ProductGroup> groups) {
        if (groups == null || groups.size() < 2) {
            return ComparisonResult.builder()
                    .groups(groups != null ? groups : List.of())
                    .summary("Need at least 2 products to compare.")
                    .dimensions(List.of())
                    .build();
        }

        List<ComparisonResult.Dimension> dimensions = new ArrayList<>();

        // Price comparison
        dimensions.add(buildDimension("Price", groups,
                g -> g.getBestPrice() != null ? "₹" + String.format("%.0f", g.getBestPrice()) : "N/A"));

        // Rating comparison
        dimensions.add(buildDimension("Rating", groups,
                g -> g.getRating() != null ? g.getRating() + "/5" : "N/A"));

        // Reviews comparison
        dimensions.add(buildDimension("Reviews", groups,
                g -> g.getReviewCount() != null ? formatReviewCount(g.getReviewCount()) : "N/A"));

        // Brand
        dimensions.add(buildDimension("Brand", groups,
                g -> g.getBrand() != null ? g.getBrand() : "N/A"));

        // Stores available
        dimensions.add(buildDimension("Available at", groups,
                g -> g.getOffers() != null
                        ? String.join(", ", g.getOffers().stream().map(o -> o.getStore()).filter(Objects::nonNull).toList())
                        : "N/A"));

        // Score comparison
        dimensions.add(buildDimension("Score", groups,
                g -> g.getFinalScore() != null ? String.format("%.2f", g.getFinalScore()) : "N/A"));

        // Badge
        dimensions.add(buildDimension("Badge", groups,
                g -> g.getBadge() != null ? g.getBadge() : "—"));

        // Generate summary
        ProductGroup winner = groups.stream()
                .max(Comparator.comparingDouble(g -> g.getFinalScore() != null ? g.getFinalScore() : 0))
                .orElse(groups.get(0));

        String summary = String.format("Based on price, rating, and value, the %s comes out ahead with a score of %.2f.",
                winner.getName(), winner.getFinalScore() != null ? winner.getFinalScore() : 0.0);

        return ComparisonResult.builder()
                .groups(groups)
                .dimensions(dimensions)
                .summary(summary)
                .winner(winner.getName())
                .build();
    }

    private ComparisonResult.Dimension buildDimension(String name, List<ProductGroup> groups,
                                                       java.util.function.Function<ProductGroup, String> extractor) {
        Map<String, String> values = new LinkedHashMap<>();
        for (ProductGroup g : groups) {
            values.put(g.getName(), extractor.apply(g));
        }
        return new ComparisonResult.Dimension(name, values);
    }

    private String formatReviewCount(int count) {
        if (count >= 1000) {
            return String.format("%.1fK", count / 1000.0);
        }
        return String.valueOf(count);
    }
}
