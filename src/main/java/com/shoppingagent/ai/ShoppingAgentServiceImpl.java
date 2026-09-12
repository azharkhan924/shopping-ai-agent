package com.shoppingagent.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingagent.ai.exception.MalformedAiResponseException;
import com.shoppingagent.ai.intent.ShoppingIntent;
import com.shoppingagent.ai.model.RequirementAnalysis;
import com.shoppingagent.ai.model.ShoppingQuery;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShoppingAgentServiceImpl implements ShoppingAgentService {

    private static final String SYSTEM_CONTEXT = """
            Analyze the user's latest shopping-related message and return ONLY a JSON
            object with this exact shape:

            {
              "intent": "PRODUCT_SEARCH | PRODUCT_COMPARISON | REFINEMENT | GENERAL_SHOPPING_QUERY | CLARIFICATION_REQUIRED",
              "shoppingQuery": {
                "category": string or null,
                "keywords": string[],
                "brands": string[],
                "excludedBrands": string[],
                "minPrice": number or null,
                "maxPrice": number or null,
                "currency": string (default "INR"),
                "minimumRating": number or null,
                "requiredSpecifications": { string: string },
                "preferredStores": string[],
                "excludedStores": string[],
                "userPreferences": { string: string },
                "sortPreference": "BEST_VALUE | LOWEST_PRICE | HIGHEST_RATED | NEWEST | NONE"
              },
              "readyToSearch": boolean,
              "missingInformation": string[],
              "clarificationQuestion": string or null
            }

            Rules:
            - If a "Previous shopping requirement so far" JSON is given below, treat the new
              message as an UPDATE/REFINEMENT to it if it refines the existing item (e.g. "make it black",
              "under 30000", "show only Flipkart", "need 16GB RAM").
            - CRITICAL: If the user changes category OR asks for a distinct specific model/product
              (e.g., switching to "MacBook Air M3", "iPhone 15", "Sony WH-1000XM5"), do NOT carry forward
              any previous price ceilings (minPrice, maxPrice) unless the user explicitly specified a budget
              in this new message. Different models and tiers have completely different price ranges.
            - readyToSearch = true only when you have at minimum a category AND (a budget OR
              at least one concrete requirement/spec/brand/model) to search on. Queries for specific
              products like "MacBook Air M3" or "iPhone 15" have both category and model/spec, so
              readyToSearch MUST be true. Missing "nice to have" details do not block search.
            - Ask at most ONE clarification question, and only the single most useful one.
              Never ask for information already present in the conversation.
            - Handle Hindi/Hinglish input naturally (e.g. "chahiye", "ke andar", "achhi
              brand ka") and extract the same structured fields from it.
            - Output raw JSON only — no markdown fences, no commentary.
            """;

    private final ShoppingAiClient shoppingAiClient;
    private final ShoppingAiProperties properties;
    private final ObjectMapper objectMapper;

    public ShoppingAgentServiceImpl(ShoppingAiClient shoppingAiClient,
                                     ShoppingAiProperties properties,
                                     ObjectMapper objectMapper) {
        this.shoppingAiClient = shoppingAiClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public RequirementAnalysis analyze(List<ChatTurn> history, ShoppingQuery previousQuery, String latestUserMessage) {
        if (latestUserMessage == null || latestUserMessage.isBlank()) {
            throw new IllegalArgumentException("latestUserMessage must not be blank");
        }

        String prompt = buildUserPrompt(history, previousQuery, latestUserMessage);
        RequirementAnalysis result = shoppingAiClient.extractRequirement(SYSTEM_CONTEXT, prompt);

        validate(result);
        return result;
    }

    private String buildUserPrompt(List<ChatTurn> history, ShoppingQuery previousQuery, String latestUserMessage) {
        StringBuilder sb = new StringBuilder();

        List<ChatTurn> windowed = windowHistory(history);
        if (!windowed.isEmpty()) {
            sb.append("Recent conversation (oldest first):\n");
            for (ChatTurn turn : windowed) {
                sb.append("- ").append(turn.role()).append(": ").append(turn.content()).append('\n');
            }
            sb.append('\n');
        }

        if (previousQuery != null && !previousQuery.isEmpty()) {
            sb.append("Previous shopping requirement so far (JSON):\n");
            sb.append(toJsonSafely(previousQuery)).append("\n\n");
        }

        sb.append("Latest user message:\n").append(latestUserMessage);
        return sb.toString();
    }

    private List<ChatTurn> windowHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int max = Math.max(0, properties.getMaxContextTurns());
        if (history.size() <= max) {
            return history;
        }
        return history.subList(history.size() - max, history.size());
    }

    private String toJsonSafely(ShoppingQuery query) {
        try {
            return objectMapper.writeValueAsString(query);
        } catch (JsonProcessingException e) {
            // Context serialization failing shouldn't break the whole request —
            // just proceed without prior context.
            return "{}";
        }
    }

    private void validate(RequirementAnalysis result) {
        if (result.getIntent() == null) {
            throw new MalformedAiResponseException("AI response missing 'intent'");
        }
        if (result.getShoppingQuery() == null) {
            throw new MalformedAiResponseException("AI response missing 'shoppingQuery'");
        }
        if (!result.isReadyToSearch() && isBlank(result.getClarificationQuestion())
                && result.getIntent() != ShoppingIntent.GENERAL_SHOPPING_QUERY) {
            throw new MalformedAiResponseException(
                    "AI response says clarification is needed but provided no clarificationQuestion");
        }
        if (result.getMissingInformation() == null) {
            result.setMissingInformation(List.of());
        }
        // Normalize currency default without rejecting the whole response over it.
        if (isBlank(result.getShoppingQuery().getCurrency())) {
            result.getShoppingQuery().setCurrency("INR");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
