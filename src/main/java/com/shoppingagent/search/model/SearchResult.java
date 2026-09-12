package com.shoppingagent.search.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shoppingagent.ai.model.ShoppingQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Module 5.8 — the clean, frontend/agent-ready shape produced by
 * {@code ProductSearchService}. Never a JPA entity, never a raw provider response.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResult {
    private final ShoppingQuery query;
    private final List<Product> products;
    private final int totalResults;
}
