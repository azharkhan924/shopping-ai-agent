package com.shoppingagent.search;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.exception.SearchException;
import com.shoppingagent.search.model.Product;
import com.shoppingagent.search.model.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Module 5.9 — the single entry point other modules should call to go from a
 * {@link ShoppingQuery} to a clean {@link SearchResult}.
 *
 * ShoppingQuery -> SearchOrchestrator -> Providers -> Products -> basic filtering -> SearchResult
 */
@Service
public class ProductSearchService {

    private final SearchOrchestrator orchestrator;
    private final SearchFilter filter;

    public ProductSearchService(SearchOrchestrator orchestrator, SearchFilter filter) {
        this.orchestrator = orchestrator;
        this.filter = filter;
    }

    /**
     * @throws SearchException if every configured provider failed. A provider
     *                          failing alongside at least one working provider
     *                          does NOT throw — partial results are returned.
     */
    public SearchResult search(ShoppingQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }

        SearchOrchestrator.OrchestrationOutcome outcome = orchestrator.searchAll(query);

        if (outcome.allProvidersFailed()) {
            throw new SearchException("I couldn't search for products right now. Please try again.");
        }

        List<Product> filtered = filter.apply(outcome.products(), query);

        if (filtered.isEmpty() && !outcome.products().isEmpty()) {
            boolean allExceedMaxPrice = query.getMaxPrice() != null && outcome.products().stream()
                    .allMatch(p -> p.getPrice() != null && p.getPrice().compareTo(java.math.BigDecimal.valueOf(query.getMaxPrice())) > 0);
            if (!allExceedMaxPrice) {
                filtered = outcome.products();
            }
        }

        return SearchResult.builder()
                .query(query)
                .products(filtered)
                .totalResults(filtered.size())
                .build();
    }
}
