package com.shoppingagent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for POST /api/chat and POST /api/chat/stream.
 */
public record ChatRequest(
        @NotNull(message = "conversationId is required")
        UUID conversationId,

        @NotBlank(message = "message must not be blank")
        String message
) {}
