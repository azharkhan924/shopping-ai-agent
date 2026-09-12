package com.shoppingagent.product.grouping;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single store's offer for a product within a ProductGroup.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductOffer {

    private String store;
    private BigDecimal price;
    private String currency;
    private String productUrl;
    private String availability;
    private String deliveryInfo;
    private BigDecimal originalPrice;
    private Double discountPercentage;
}
