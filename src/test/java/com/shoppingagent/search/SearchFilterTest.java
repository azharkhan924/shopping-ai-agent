package com.shoppingagent.search;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchFilterTest {

    private final SearchFilter filter = new SearchFilter();

    private Product product(String name, String category, String brand, double price, Double rating) {
        return Product.builder()
                .id(name).name(name).category(category).brand(brand)
                .price(BigDecimal.valueOf(price)).currency("INR").rating(rating)
                .build();
    }

    @Test
    void filtersByMaxPrice() {
        List<Product> products = List.of(
                product("cheap", "pendrive", "SanDisk", 800, 4.0),
                product("expensive", "pendrive", "SanDisk", 1500, 4.0)
        );

        List<Product> result = filter.apply(products, ShoppingQuery.builder().maxPrice(1000.0).build());

        assertThat(result).extracting(Product::getName).containsExactly("cheap");
    }

    @Test
    void filtersByCategory() {
        List<Product> products = List.of(
                product("pd", "pendrive", "SanDisk", 800, 4.0),
                product("hp", "headphones", "boAt", 800, 4.0)
        );

        List<Product> result = filter.apply(products, ShoppingQuery.builder().category("headphones").build());

        assertThat(result).extracting(Product::getName).containsExactly("hp");
    }

    @Test
    void respectsBrandInclusionList() {
        List<Product> products = List.of(
                product("a", "pendrive", "SanDisk", 800, 4.0),
                product("b", "pendrive", "Kingston", 800, 4.0)
        );

        List<Product> result = filter.apply(products,
                ShoppingQuery.builder().brands(List.of("SanDisk")).build());

        assertThat(result).extracting(Product::getName).containsExactly("a");
    }

    @Test
    void respectsExcludedBrands() {
        List<Product> products = List.of(
                product("a", "pendrive", "SanDisk", 800, 4.0),
                product("b", "pendrive", "Kingston", 800, 4.0)
        );

        List<Product> result = filter.apply(products,
                ShoppingQuery.builder().excludedBrands(List.of("Kingston")).build());

        assertThat(result).extracting(Product::getName).containsExactly("a");
    }

    @Test
    void unknownFieldsDoNotExcludeProducts() {
        Product noPrice = Product.builder().id("x").name("x").category("pendrive").build();

        List<Product> result = filter.apply(List.of(noPrice), ShoppingQuery.builder().maxPrice(500.0).build());

        assertThat(result).containsExactly(noPrice);
    }

    @Test
    void macbookAirM3_matchesCategoryAndSpecSubstring() {
        Product macbook = Product.builder()
                .id("mac1")
                .name("Apple MacBook Air 13-inch (M3 Chip, 8GB Unified Memory)")
                .category("laptop")
                .brand("Apple")
                .price(BigDecimal.valueOf(104990))
                .currency("INR")
                .specifications(java.util.Map.of("Processor", "Apple M3 8-core CPU", "RAM", "8GB Unified Memory"))
                .build();

        // Query with category "macbook" and spec "M3"
        ShoppingQuery query = ShoppingQuery.builder()
                .category("macbook")
                .requiredSpecifications(java.util.Map.of("processor", "M3"))
                .brands(List.of("Apple"))
                .build();

        List<Product> result = filter.apply(List.of(macbook), query);

        assertThat(result).containsExactly(macbook);
    }

    @Test
    void airpods_matchesHeadphonesSynonym() {
        Product airpods = Product.builder()
                .id("ap1")
                .name("Apple AirPods Pro (2nd Gen)")
                .category("headphones")
                .brand("Apple")
                .price(BigDecimal.valueOf(19990))
                .currency("INR")
                .build();

        ShoppingQuery query = ShoppingQuery.builder()
                .category("airpods")
                .build();

        List<Product> result = filter.apply(List.of(airpods), query);

        assertThat(result).containsExactly(airpods);
    }
}
