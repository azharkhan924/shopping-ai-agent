package com.shoppingagent.ai.intent;

/**
 * Coarse-grained shopping intent detected from the user's latest message.
 * Kept deliberately small per Module 4 spec — do not overcomplicate intent detection.
 */
public enum ShoppingIntent {
    PRODUCT_SEARCH,
    PRODUCT_COMPARISON,
    REFINEMENT,
    GENERAL_SHOPPING_QUERY,
    CLARIFICATION_REQUIRED
}
