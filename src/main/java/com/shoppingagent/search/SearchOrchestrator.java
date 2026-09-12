package com.shoppingagent.search;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.model.Product;
import com.shoppingagent.search.model.ProviderSearchResult;
import com.shoppingagent.search.provider.ProductSearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Module 5.5 — runs every enabled {@link ProductSearchProvider}, isolates
 * failures so one broken provider never breaks the whole search, and combines
 * the results. Which providers are "enabled" is decided entirely by Spring
 * (each provider bean is conditionally created based on SHOPPING_SEARCH_MODE
 * etc.) — this class just uses whatever list it's given.
 */
@Component
public class SearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SearchOrchestrator.class);

    private final List<ProductSearchProvider> providers;

    public SearchOrchestrator(List<ProductSearchProvider> providers) {
        this.providers = providers;
    }

    /**
     * Searches every registered provider and returns the combined, minimally
     * sanity-checked product list plus per-provider diagnostics.
     */
    public OrchestrationOutcome searchAll(ShoppingQuery query) {
        List<ProviderSearchResult> providerResults = new ArrayList<>();
        List<Product> combined = new ArrayList<>();

        for (ProductSearchProvider provider : providers) {
            long start = System.currentTimeMillis();
            try {
                List<Product> results = provider.search(query);
                List<Product> valid = results.stream()
                        .filter(this::isValid)
                        .toList();

                providerResults.add(ProviderSearchResult.builder()
                        .provider(provider.getProviderName())
                        .products(valid)
                        .success(true)
                        .searchDurationMillis(System.currentTimeMillis() - start)
                        .build());

                combined.addAll(valid);
            } catch (Exception e) {
                log.warn("Search provider '{}' failed: {}", provider.getProviderName(), e.getMessage());
                providerResults.add(ProviderSearchResult.builder()
                        .provider(provider.getProviderName())
                        .products(List.of())
                        .success(false)
                        .error(e.getMessage())
                        .searchDurationMillis(System.currentTimeMillis() - start)
                        .build());
            }
        }

        boolean allProvidersFailed = !providerResults.isEmpty()
                && providerResults.stream().noneMatch(ProviderSearchResult::isSuccess);
        return new OrchestrationOutcome(combined, providerResults, allProvidersFailed);
    }

    /**
     * Removes obviously invalid results (Module 5.5 step 5) — missing identity,
     * negative/zero price where a price is present, etc. This is NOT the
     * deterministic requirement filtering (category/price/brand) — that's
     * {@link SearchFilter}, applied afterwards by {@link ProductSearchService}.
     */
    private boolean isValid(Product product) {
        if (product == null) return false;
        if (product.getId() == null || product.getId().isBlank()) return false;
        if (product.getName() == null || product.getName().isBlank()) return false;
        if (product.getPrice() != null && product.getPrice().signum() < 0) return false;
        return true;
    }

    /**
     * @param products           combined, validity-checked products from all providers
     * @param providerResults    per-provider diagnostics
     * @param allProvidersFailed true only when at least one provider was configured
     *                           and every single one of them failed
     */
    public record OrchestrationOutcome(List<Product> products,
                                        List<ProviderSearchResult> providerResults,
                                        boolean allProvidersFailed) {
        public OrchestrationOutcome {
            Objects.requireNonNull(products);
            Objects.requireNonNull(providerResults);
        }
    }
}
