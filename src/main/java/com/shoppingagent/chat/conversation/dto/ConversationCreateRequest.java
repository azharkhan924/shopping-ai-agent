package com.shoppingagent.chat.conversation.dto;

import jakarta.validation.constraints.Size;

public record ConversationCreateRequest(
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title
) {
}
