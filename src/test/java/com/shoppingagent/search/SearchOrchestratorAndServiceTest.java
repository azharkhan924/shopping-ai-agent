package com.shoppingagent.search;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.exception.SearchException;
import com.shoppingagent.search.model.Product;
import com.shoppingagent.search.model.SearchResult;
import com.shoppingagent.search.provider.ProductSearchProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchOrchestratorAndServiceTest {

    private Product product(String id, String category, double price) {
        return Product.builder().id(id).name(id).category(category)
                .price(BigDecimal.valueOf(price)).currency("INR").build();
    }

    private ProductSearchProvider workingProvider(String name, List<Product> products) {
        return new ProductSearchProvider() {
            @Override
            public List<Product> search(ShoppingQuery query) {
                return products;
            }

            @Override
            public String getProviderName() {
                return name;
            }
        };
    }

    private ProductSearchProvider failingProvider(String name) {
        return new ProductSearchProvider() {
            @Override
            public List<Product> search(ShoppingQuery query) {
                throw new RuntimeException("provider exploded");
            }

            @Override
            public String getProviderName() {
                return name;
            }
        };
    }

    @Test
    void combinesResultsFromMultipleProviders() {
        var providerA = workingProvider("A", List.of(product("a1", "pendrive", 800), product("a2", "pendrive", 900)));
        var providerB = workingProvider("B", List.of(product("b1", "pendrive", 850)));
        SearchOrchestrator orchestrator = new SearchOrchestrator(List.of(providerA, providerB));

        var outcome = orchestrator.searchAll(ShoppingQuery.builder().category("pendrive").build());

        assertThat(outcome.products()).hasSize(3);
        assertThat(outcome.allProvidersFailed()).isFalse();
    }

    @Test
    void oneProviderFailing_doesNotBreakOverallSearch() {
        var providerA = workingProvider("A", List.of(product("a1", "pendrive", 800)));
        var providerC = failingProvider("C");
        SearchOrchestrator orchestrator = new SearchOrchestrator(List.of(providerA, providerC));

        var outcome = orchestrator.searchAll(ShoppingQuery.builder().build());

        assertThat(outcome.products()).hasSize(1);
        assertThat(outcome.allProvidersFailed()).isFalse();
        assertThat(outcome.providerResults()).anyMatch(r -> !r.isSuccess() && r.getProvider().equals("C"));
    }

    @Test
    void allProvidersFailing_marksOutcomeAsFailed() {
        SearchOrchestrator orchestrator = new SearchOrchestrator(List.of(failingProvider("A"), failingProvider("B")));

        var outcome = orchestrator.searchAll(ShoppingQuery.builder().build());

        assertThat(outcome.allProvidersFailed()).isTrue();
        assertThat(outcome.products()).isEmpty();
    }

    @Test
    void productSearchService_throwsSearchException_whenAllProvidersFail() {
        SearchOrchestrator orchestrator = new SearchOrchestrator(List.of(failingProvider("A")));
        ProductSearchService service = new ProductSearchService(orchestrator, new SearchFilter());

        assertThatThrownBy(() -> service.search(ShoppingQuery.builder().build()))
                .isInstanceOf(SearchException.class);
    }

    @Test
    void productSearchService_returnsNoResults_whenFilteredToEmpty() {
        var providerA = workingProvider("A", List.of(product("a1", "pendrive", 2000)));
        SearchOrchestrator orchestrator = new SearchOrchestrator(List.of(providerA));
        ProductSearchService service = new ProductSearchService(orchestrator, new SearchFilter());

        SearchResult result = service.search(ShoppingQuery.builder().category("pendrive").maxPrice(1000.0).build());

        assertThat(result.getProducts()).isEmpty();
        assertThat(result.getTotalResults()).isZero();
    }

    @Test
    void productSearchService_appliesPriceFiltering() {
        var providerA = workingProvider("A", List.of(
                product("cheap", "pendrive", 800),
                product("expensive", "pendrive", 1500)));
        SearchOrchestrator orchestrator = new SearchOrchestrator(List.of(providerA));
        ProductSearchService service = new ProductSearchService(orchestrator, new SearchFilter());

        SearchResult result = service.search(ShoppingQuery.builder().category("pendrive").maxPrice(1000.0).build());

        assertThat(result.getProducts()).extracting(Product::getId).containsExactly("cheap");
    }
}
