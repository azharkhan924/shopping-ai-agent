package com.shoppingagent.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured, provider-independent representation of what the user is shopping for.
 * This is produced by {@code ShoppingAgentService} from natural-language input and is the
 * ONLY thing the search layer (Module 5) ever consumes — the LLM never talks to the
 * search layer directly.
 *
 * All fields are optional; a field being null/empty simply means "not specified".
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShoppingQuery {

    private String category;

    @Builder.Default
    private List<String> keywords = new ArrayList<>();

    @Builder.Default
    private List<String> brands = new ArrayList<>();

    @Builder.Default
    private List<String> excludedBrands = new ArrayList<>();

    private Double minPrice;

    private Double maxPrice;

    @Builder.Default
    private String currency = "INR";

    private Double minimumRating;

    @Builder.Default
    private Map<String, String> requiredSpecifications = new LinkedHashMap<>();

    @Builder.Default
    private List<String> preferredStores = new ArrayList<>();

    @Builder.Default
    private List<String> excludedStores = new ArrayList<>();

    @Builder.Default
    private Map<String, String> userPreferences = new LinkedHashMap<>();

    @Builder.Default
    private SortPreference sortPreference = SortPreference.BEST_VALUE;

    /**
     * True if this query has essentially no information yet (fresh/empty).
     */
    public boolean isEmpty() {
        return category == null
                && (keywords == null || keywords.isEmpty())
                && (brands == null || brands.isEmpty())
                && minPrice == null
                && maxPrice == null
                && (requiredSpecifications == null || requiredSpecifications.isEmpty());
    }
}
