package com.shoppingagent.ai;

import com.shoppingagent.ai.model.RequirementAnalysis;
import com.shoppingagent.ai.model.ShoppingQuery;

import java.util.List;

/**
 * Module 4 entry point: understands natural-language shopping requests.
 *
 * This is the ONLY class other modules (chat integration, future comparison/
 * ranking) should depend on for "what does the AI think the user wants". It
 * never talks to the database and never talks to the product-search layer —
 * see PROJECT_CONTEXT.md section 13 (loose coupling) and Module 5.13
 * (separation of responsibilities).
 */
public interface ShoppingAgentService {

    /**
     * Analyzes the latest user message in light of recent conversation history
     * and (optionally) a previously-extracted ShoppingQuery, and returns a fresh
     * {@link RequirementAnalysis}.
     *
     * @param history            recent turns, oldest first. Only the most recent
     *                           {@code shopping.ai.max-context-turns} are actually
     *                           sent to the LLM.
     * @param previousQuery      the ShoppingQuery extracted from this conversation so
     *                           far, or null if this is the first shopping-related
     *                           message. Used so a refinement ("battery life is most
     *                           important") updates the existing query instead of
     *                           starting a new, unrelated one.
     * @param latestUserMessage  the new message to analyze.
     */
    RequirementAnalysis analyze(List<ChatTurn> history, ShoppingQuery previousQuery, String latestUserMessage);
}
