package com.shoppingagent.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Internal bookkeeping for one provider's attempt within a single search — never
 * returned to the frontend directly (see Module 5.6). Makes debugging/logging
 * "provider A gave 10, provider B errored" straightforward.
 */
@Getter
@Builder
@AllArgsConstructor
public class ProviderSearchResult {
    private final String provider;
    private final List<Product> products;
    private final boolean success;
    private final String error;
    private final long searchDurationMillis;
}
