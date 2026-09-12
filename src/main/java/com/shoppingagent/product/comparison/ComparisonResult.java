package com.shoppingagent.product.comparison;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shoppingagent.product.grouping.ProductGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Structured comparison result for side-by-side product comparison.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComparisonResult {

    private List<ProductGroup> groups;
    private List<Dimension> dimensions;
    private String summary;
    private String winner;

    /**
     * A single comparison dimension (e.g., "Price", "Rating").
     * Values maps product-name to its value for this dimension.
     */
    public record Dimension(String name, Map<String, String> values) {}
}
