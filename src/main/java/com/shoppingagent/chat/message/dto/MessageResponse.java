package com.shoppingagent.chat.message.dto;

import com.shoppingagent.chat.message.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        MessageRole role,
        String content,
        Instant createdAt
) {
}
