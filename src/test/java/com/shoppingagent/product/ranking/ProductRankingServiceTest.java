package com.shoppingagent.product.ranking;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.product.grouping.ProductGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductRankingServiceTest {

    private final ProductRankingService rankingService = new ProductRankingService();

    @Test
    void ranksHigherRatedAndBetterPriceFirst() {
        ProductGroup cheapAndGood = ProductGroup.builder()
                .name("Budget King")
                .bestPrice(1000.0)
                .rating(4.8)
                .reviewCount(500)
                .build();

        ProductGroup expensiveAndLowRated = ProductGroup.builder()
                .name("Overpriced Low")
                .bestPrice(5000.0)
                .rating(3.2)
                .reviewCount(10)
                .build();

        ShoppingQuery query = ShoppingQuery.builder().category("headphones").build();
        List<ProductGroup> ranked = rankingService.rank(List.of(expensiveAndLowRated, cheapAndGood), query);

        assertEquals(2, ranked.size());
        assertEquals("Budget King", ranked.get(0).getName());
        assertTrue(ranked.get(0).getFinalScore() > ranked.get(1).getFinalScore());
    }
}
