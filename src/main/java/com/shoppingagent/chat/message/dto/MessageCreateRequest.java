package com.shoppingagent.chat.message.dto;

import jakarta.validation.constraints.NotBlank;

public record MessageCreateRequest(
        @NotBlank(message = "Content is required")
        String content
) {
}
