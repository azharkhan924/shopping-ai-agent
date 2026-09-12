package com.shoppingagent.ai;

/**
 * A single, minimal conversation turn used as LLM context. Deliberately decoupled
 * from the existing {@code com.shoppingagent.chat.message.Message} entity so this
 * module has no compile-time dependency on Module 3's persistence types — the
 * caller (e.g. a controller or orchestration service) maps its own Message rows
 * into these before calling {@link ShoppingAgentService}.
 *
 * @param role    "USER" or "ASSISTANT" (matches MessageRole.name())
 * @param content the message text
 */
public record ChatTurn(String role, String content) {
}
