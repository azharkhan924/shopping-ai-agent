package com.shoppingagent.agent;

import com.shoppingagent.ai.ChatTurn;
import com.shoppingagent.ai.model.ShoppingQuery;

import java.util.List;

/**
 * Module 5.10 — connects Modules 3 + 4 + 5 for the chat/message flow, WITHOUT
 * taking any compile-time dependency on the existing chat/message/conversation
 * entities or services (those already belong to Modules 1-3 and are untouched).
 *
 * Intended usage from the existing message endpoint
 * (POST /api/conversations/{id}/messages), after the USER message has already
 * been persisted by the existing MessageService:
 *
 * <pre>
 *   List&lt;ChatTurn&gt; history = existingMessages.stream()
 *       .map(m -&gt; new ChatTurn(m.getRole().name(), m.getContent()))
 *       .toList();
 *
 *   AgentResponse response = shoppingChatOrchestrationService
 *       .handleUserMessage(conversationId.toString(), history, previousQuery, newMessageContent);
 *
 *   messageService.saveMessage(conversation, MessageRole.ASSISTANT, response.getMessage().getContent());
 *   return response; // or map to your own controller response DTO
 * </pre>
 *
 * Where does {@code previousQuery} come from? This module does not persist
 * ShoppingQuery anywhere (no new tables were added, per the "preserve Modules
 * 1-3" constraint) — callers are expected to either (a) keep it in-memory per
 * conversation for now, or (b) re-derive it by passing a longer history window
 * and letting {@code ShoppingAgentService} rebuild it from context each time.
 * Passing {@code null} is always safe — it degrades gracefully to "we don't
 * know the running query yet".
 */
public interface ShoppingChatOrchestrationService {

    AgentResponse handleUserMessage(String conversationId,
                                     List<ChatTurn> history,
                                     ShoppingQuery previousQuery,
                                     String latestUserMessage);
}
