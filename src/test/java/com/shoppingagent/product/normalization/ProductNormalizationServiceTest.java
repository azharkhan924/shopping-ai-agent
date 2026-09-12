package com.shoppingagent.product.normalization;

import com.shoppingagent.search.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductNormalizationServiceTest {

    private final ProductNormalizationService service = new ProductNormalizationService();

    @Test
    void normalizesBrandAliases() {
        Product p1 = Product.builder().name("Cruzer Blade").brand("san disk").category("pen drive").build();
        Product p2 = Product.builder().name("Airdopes 141").brand("boat").category("earbuds").build();
        Product p3 = Product.builder().name("Unknown Product").brand("RandomBrand").category("shoes").build();

        List<Product> normalized = service.normalize(List.of(p1, p2, p3));

        assertEquals("SanDisk", normalized.get(0).getBrand());
        assertEquals("pendrive", normalized.get(0).getCategory());

        assertEquals("boAt", normalized.get(1).getBrand());
        assertEquals("headphones", normalized.get(1).getCategory());

        assertEquals("RandomBrand", normalized.get(2).getBrand());
        assertEquals("shoes", normalized.get(2).getCategory());
    }

    @Test
    void normalizesWhitespaceInNames() {
        Product p = Product.builder().name("  Sony   WH-1000XM5   Wireless  ").brand("sony").build();
        List<Product> normalized = service.normalize(List.of(p));

        assertEquals("Sony WH-1000XM5 Wireless", normalized.get(0).getName());
        assertEquals("Sony", normalized.get(0).getBrand());
    }
}
