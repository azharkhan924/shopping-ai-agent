package com.shoppingagent.product.grouping;

import com.shoppingagent.product.dedup.DuplicateDetectionService;
import com.shoppingagent.search.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductGroupingServiceTest {

    private final ProductGroupingService service = new ProductGroupingService(new DuplicateDetectionService());

    @Test
    void groupsOffersAndFindsBestPrice() {
        Product p1 = Product.builder()
                .name("SanDisk Ultra 128GB Flash Drive")
                .brand("SanDisk")
                .category("pendrive")
                .store("Amazon")
                .price(BigDecimal.valueOf(899))
                .currency("INR")
                .rating(4.5)
                .reviewCount(1200)
                .build();

        Product p2 = Product.builder()
                .name("SanDisk Ultra 128GB Pen Drive")
                .brand("SanDisk")
                .category("pendrive")
                .store("Flipkart")
                .price(BigDecimal.valueOf(799))
                .currency("INR")
                .rating(4.4)
                .reviewCount(800)
                .build();

        List<ProductGroup> groups = service.group(List.of(p1, p2));

        assertEquals(1, groups.size());
        ProductGroup group = groups.get(0);
        assertEquals("SanDisk", group.getBrand());
        assertEquals(2, group.getOffers().size());
        assertEquals(799.0, group.getBestPrice());
        assertEquals("Flipkart", group.getOffers().get(0).getStore()); // cheapest offer first
    }
}
