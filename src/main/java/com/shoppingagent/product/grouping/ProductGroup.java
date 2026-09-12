package com.shoppingagent.product.grouping;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shoppingagent.search.model.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 6.3 — A group of products that are the same item available from different stores.
 * The "representative" product is the one with the best rating/most reviews.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductGroup {

    private String groupId;
    private String name;
    private String brand;
    private String category;
    private String description;
    private String imageUrl;
    private Double rating;
    private Integer reviewCount;

    @Builder.Default
    private List<ProductOffer> offers = new ArrayList<>();

    /** Best price among all offers */
    private Double bestPrice;
    /** Currency */
    private String currency;

    /** Populated by ranking/badge service */
    private String badge;
    private Double finalScore;
    private String reason;

    /** Original representative product (for specs access) */
    private Product representativeProduct;
}
