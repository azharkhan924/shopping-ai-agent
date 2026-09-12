package com.shoppingagent.search.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider-independent, normalized product representation. This is what
 * {@link com.shoppingagent.search.provider.ProductSearchProvider} implementations
 * return and what the future agent/frontend consumes — never a provider's raw
 * response shape.
 *
 * Every field except id/name/source is nullable: real providers won't populate
 * everything, and callers must treat missing fields as "unavailable", never
 * infer or fabricate them (see Module 5.12).
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Product {

    private String id;
    private String name;
    private String brand;
    private String category;
    private String description;
    private String imageUrl;

    private BigDecimal price;
    private String currency;
    private BigDecimal originalPrice;
    private Double discountPercentage;

    private Double rating;
    private Integer reviewCount;

    private String store;
    private String productUrl;
    private Availability availability;
    private String deliveryInfo;

    @Builder.Default
    private Map<String, String> specifications = new LinkedHashMap<>();

    /**
     * Which provider produced this result, e.g. "MOCK". Never presented to the
     * user as a real-time source without qualification.
     */
    private String source;

    private Instant lastCheckedAt;
}
