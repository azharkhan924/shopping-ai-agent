package com.shoppingagent.product.ranking;

import com.shoppingagent.product.grouping.ProductGroup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BadgeAssignmentServiceTest {

    private final BadgeAssignmentService badgeService = new BadgeAssignmentService();

    @Test
    void assignsBadgesCorrectly() {
        ProductGroup top = ProductGroup.builder()
                .name("Top Overall")
                .bestPrice(2000.0)
                .rating(4.5)
                .reviewCount(500)
                .finalScore(0.95)
                .build();

        ProductGroup cheap = ProductGroup.builder()
                .name("Budget Deal")
                .bestPrice(500.0)
                .rating(4.0)
                .reviewCount(200)
                .finalScore(0.80)
                .build();

        ProductGroup highRated = ProductGroup.builder()
                .name("Critically Acclaimed")
                .bestPrice(3000.0)
                .rating(4.9)
                .reviewCount(100)
                .finalScore(0.75)
                .build();

        List<ProductGroup> groups = new ArrayList<>(List.of(top, cheap, highRated));
        badgeService.assignBadges(groups);

        assertEquals("BEST_OVERALL", top.getBadge());
        assertEquals("CHEAPEST", cheap.getBadge());
        assertEquals("HIGHEST_RATED", highRated.getBadge());
    }
}
