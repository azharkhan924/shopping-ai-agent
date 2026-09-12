package com.shoppingagent.auth.dto;

import com.shoppingagent.user.dto.UserResponse;

public record RegisterResponse(
        String message,
        UserResponse user
) {
}
