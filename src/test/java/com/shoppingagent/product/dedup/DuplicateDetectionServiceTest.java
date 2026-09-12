package com.shoppingagent.product.dedup;

import com.shoppingagent.search.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateDetectionServiceTest {

    private final DuplicateDetectionService service = new DuplicateDetectionService();

    @Test
    void detectsDuplicateProductsFromDifferentStores() {
        Product p1 = Product.builder()
                .name("Sony WH-1000XM5 Wireless Headphones")
                .brand("Sony")
                .category("headphones")
                .store("Amazon")
                .build();

        Product p2 = Product.builder()
                .name("Sony WH-1000XM5 Bluetooth Noise Cancelling Headphones")
                .brand("Sony")
                .category("headphones")
                .store("Flipkart")
                .build();

        Product p3 = Product.builder()
                .name("Apple AirPods Pro 2nd Gen")
                .brand("Apple")
                .category("headphones")
                .store("Amazon")
                .build();

        List<List<Product>> groups = service.detectDuplicateGroups(List.of(p1, p2, p3));

        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).size()); // p1 and p2 grouped together
        assertEquals(1, groups.get(1).size()); // p3 in separate group
    }

    @Test
    void handlesEmptyList() {
        List<List<Product>> groups = service.detectDuplicateGroups(List.of());
        assertTrue(groups.isEmpty());
    }
}
