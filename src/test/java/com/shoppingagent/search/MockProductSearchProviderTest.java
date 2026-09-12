package com.shoppingagent.search;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.model.Product;
import com.shoppingagent.search.provider.MockProductSearchProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockProductSearchProviderTest {

    private final MockProductSearchProvider provider = new MockProductSearchProvider();

    @Test
    void returnsProductsForKnownCategory() {
        List<Product> results = provider.search(ShoppingQuery.builder().category("pendrive").build());

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(p -> p.getCategory().equalsIgnoreCase("pendrive"));
        assertThat(results).allMatch(p -> p.getSource().equals("MOCK"));
    }

    @Test
    void returnsAllProductsWhenNoCategorySpecified() {
        List<Product> results = provider.search(ShoppingQuery.builder().build());

        assertThat(results.size()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void providerName_isMock() {
        assertThat(provider.getProviderName()).isEqualTo("MOCK");
    }
}
