package com.shoppingagent.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the AI provider used by the shopping agent.
 * Bound from `shopping.ai.*`, which in turn is populated from environment
 * variables AI_API_KEY / AI_MODEL (see application.yml). Kept separate from the
 * Spring AI auto-configured properties so this module doesn't hardcode which
 * concrete provider (OpenAI, Azure, Anthropic, etc.) is behind the ChatClient —
 * swapping the Spring AI starter dependency is enough to change providers.
 */
@ConfigurationProperties(prefix = "shopping.ai")
public class ShoppingAiProperties {

    /**
     * Max number of most-recent conversation turns included as context when
     * calling the LLM. Keeps prompts small — see Module 4.6 (avoid sending huge history).
     */
    private int maxContextTurns = 8;

    /**
     * Timeout (ms) applied around the LLM call by the calling service.
     */
    private long timeoutMillis = 15000;

    public int getMaxContextTurns() {
        return maxContextTurns;
    }

    public void setMaxContextTurns(int maxContextTurns) {
        this.maxContextTurns = maxContextTurns;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
}
