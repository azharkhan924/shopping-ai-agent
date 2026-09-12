package com.shoppingagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shoppingagent.product.comparison.ComparisonResult;
import com.shoppingagent.product.grouping.ProductGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Structured response for POST /api/chat.
 * Frontend-ready — can be directly rendered.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    private String conversationId;
    private String status;
    private String message;
    private List<ProductGroupDto> products;
    private ComparisonResult comparison;

    /**
     * Frontend-ready product group DTO.
     * Strips internal fields (representativeProduct) and keeps only what the UI needs.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProductGroupDto {
        private String groupId;
        private String name;
        private String brand;
        private String category;
        private String imageUrl;
        private Double rating;
        private Integer reviewCount;
        private List<ProductOfferDto> offers;
        private Double bestPrice;
        private String currency;
        private String badge;
        private Double finalScore;
        private String reason;
    }

    /**
     * Frontend-ready offer DTO.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProductOfferDto {
        private String store;
        private Double price;
        private String currency;
        private String productUrl;
        private String availability;
        private String deliveryInfo;
        private Double originalPrice;
        private Double discountPercentage;
    }

    /**
     * Convert a ProductGroup to its frontend-safe DTO.
     */
    public static ProductGroupDto toDto(ProductGroup group) {
        List<ProductOfferDto> offerDtos = group.getOffers() == null ? List.of() :
                group.getOffers().stream().map(o -> ProductOfferDto.builder()
                        .store(o.getStore())
                        .price(o.getPrice() != null ? o.getPrice().doubleValue() : null)
                        .currency(o.getCurrency())
                        .productUrl(o.getProductUrl())
                        .availability(o.getAvailability())
                        .deliveryInfo(o.getDeliveryInfo())
                        .originalPrice(o.getOriginalPrice() != null ? o.getOriginalPrice().doubleValue() : null)
                        .discountPercentage(o.getDiscountPercentage())
                        .build()).toList();

        return ProductGroupDto.builder()
                .groupId(group.getGroupId())
                .name(group.getName())
                .brand(group.getBrand())
                .category(group.getCategory())
                .imageUrl(group.getImageUrl())
                .rating(group.getRating())
                .reviewCount(group.getReviewCount())
                .offers(offerDtos)
                .bestPrice(group.getBestPrice())
                .currency(group.getCurrency())
                .badge(group.getBadge())
                .finalScore(group.getFinalScore())
                .reason(group.getReason())
                .build();
    }
}
