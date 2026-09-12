package com.shoppingagent.search.provider;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.model.Product;

import java.util.List;

/**
 * Abstraction over "a place products can be found". The agent and orchestrator
 * never know or care whether an implementation is a mock catalog, a scraper, or
 * a paid API — new providers can be added later purely by implementing this
 * interface and registering a Spring bean; nothing else changes.
 */
public interface ProductSearchProvider {

    /**
     * @return products matching the query as best this provider can. May return
     *         an empty list. Implementations should throw on genuine failure
     *         (network error, bad response, etc.) rather than silently returning
     *         an empty list, so {@code SearchOrchestrator} can distinguish
     *         "no results" from "this provider is broken".
     */
    List<Product> search(ShoppingQuery query);

    /**
     * Short, stable name used in logs and {@code ProviderSearchResult} /
     * {@code Product.source}, e.g. "MOCK".
     */
    String getProviderName();
}
