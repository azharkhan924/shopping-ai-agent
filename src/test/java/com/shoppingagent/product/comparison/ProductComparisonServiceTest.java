package com.shoppingagent.product.comparison;

import com.shoppingagent.product.grouping.ProductGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductComparisonServiceTest {

    private final ProductComparisonService comparisonService = new ProductComparisonService();

    @Test
    void comparesProductsAcrossDimensions() {
        ProductGroup g1 = ProductGroup.builder()
                .name("Phone A")
                .bestPrice(15000.0)
                .rating(4.5)
                .reviewCount(500)
                .finalScore(0.9)
                .badge("BEST_OVERALL")
                .build();

        ProductGroup g2 = ProductGroup.builder()
                .name("Phone B")
                .bestPrice(12000.0)
                .rating(4.2)
                .reviewCount(300)
                .finalScore(0.8)
                .badge("CHEAPEST")
                .build();

        ComparisonResult result = comparisonService.compare(List.of(g1, g2));

        assertNotNull(result);
        assertFalse(result.getDimensions().isEmpty());
        assertTrue(result.getSummary().contains("Phone A"));
    }

    @Test
    void handlesSingleOrEmptyProductList() {
        ComparisonResult single = comparisonService.compare(List.of(ProductGroup.builder().name("Solo").build()));
        assertEquals("Need at least 2 products to compare.", single.getSummary());

        ComparisonResult empty = comparisonService.compare(List.of());
        assertEquals("Need at least 2 products to compare.", empty.getSummary());
    }
}
