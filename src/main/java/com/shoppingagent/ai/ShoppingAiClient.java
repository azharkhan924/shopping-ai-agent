package com.shoppingagent.ai;

import com.shoppingagent.ai.model.RequirementAnalysis;

/**
 * Thin boundary around the LLM call itself. Kept separate from
 * {@link ShoppingAgentService} so prompt construction / history handling stays
 * independent of the raw "call the model and parse structured output" concern —
 * and so it's easy to mock in tests without touching Spring AI at all.
 */
public interface ShoppingAiClient {

    /**
     * Sends the given prompt to the LLM and parses the response into a
     * {@link RequirementAnalysis}.
     *
     * @param systemContext additional system-level instructions for this call
     *                      (on top of the default system prompt configured in
     *                      {@link ShoppingAiConfig}).
     * @param userPrompt    the fully-assembled user-turn prompt, including any
     *                      conversation context and the running ShoppingQuery.
     * @throws com.shoppingagent.ai.exception.AiServiceException if the provider is
     *         unavailable, times out, or returns output that cannot be parsed.
     */
    RequirementAnalysis extractRequirement(String systemContext, String userPrompt);
}
