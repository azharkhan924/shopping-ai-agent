package com.shoppingagent.product.dedup;

import com.shoppingagent.search.model.Product;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 6.2 — Detects duplicate products across providers using
 * token-based similarity on name + brand + key specs.
 */
@Service
public class DuplicateDetectionService {

    private static final double SIMILARITY_THRESHOLD = 0.65;

    /**
     * Groups products that are likely the same item (from different stores).
     * Returns a list of groups, where each group is a list of products deemed duplicates.
     */
    public List<List<Product>> detectDuplicateGroups(List<Product> products) {
        if (products == null || products.isEmpty()) return List.of();

        List<List<Product>> groups = new ArrayList<>();
        boolean[] assigned = new boolean[products.size()];

        for (int i = 0; i < products.size(); i++) {
            if (assigned[i]) continue;

            List<Product> group = new ArrayList<>();
            group.add(products.get(i));
            assigned[i] = true;

            for (int j = i + 1; j < products.size(); j++) {
                if (assigned[j]) continue;

                if (areDuplicates(products.get(i), products.get(j))) {
                    group.add(products.get(j));
                    assigned[j] = true;
                }
            }
            groups.add(group);
        }

        return groups;
    }

    boolean areDuplicates(Product a, Product b) {
        if (hasConflictingSpecs(a.getName(), b.getName())) {
            return false;
        }

        double nameSim = tokenSimilarity(a.getName(), b.getName());
        double brandSim = exactMatch(a.getBrand(), b.getBrand());
        double categorySim = exactMatch(a.getCategory(), b.getCategory());

        // Weighted combination: name matters most
        double score = (nameSim * 0.6) + (brandSim * 0.25) + (categorySim * 0.15);
        return score >= SIMILARITY_THRESHOLD;
    }

    private boolean hasConflictingSpecs(String nameA, String nameB) {
        if (nameA == null || nameB == null) return false;
        String a = nameA.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String b = nameB.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");

        // Exclusive spec pairs across hardware generations and capacities
        String[][] exclusiveGroups = {
                {"8gb", "16gb", "24gb", "32gb", "64gb"},
                {"128gb", "256gb", "512gb", "1tb", "2tb"},
                {"m1", "m2", "m3", "m4"},
                {"i3", "i5", "i7", "i9"}
        };

        for (String[] group : exclusiveGroups) {
            for (int i = 0; i < group.length; i++) {
                for (int j = i + 1; j < group.length; j++) {
                    String s1 = group[i];
                    String s2 = group[j];
                    if (a.contains(s1) && b.contains(s2) && !a.contains(s2) && !b.contains(s1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Jaccard similarity on word tokens.
     */
    double tokenSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;

        Set<String> tokensA = tokenize(a);
        Set<String> tokensB = tokenize(b);

        if (tokensA.isEmpty() && tokensB.isEmpty()) return 1.0;
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        return (double) intersection.size() / union.size();
    }

    private double exactMatch(String a, String b) {
        if (a == null && b == null) return 1.0;
        if (a == null || b == null) return 0.0;
        return a.equalsIgnoreCase(b) ? 1.0 : 0.0;
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[\\s\\-_/]+"))
                .filter(t -> !t.isBlank() && t.length() > 1)
                .collect(Collectors.toSet());
    }
}
