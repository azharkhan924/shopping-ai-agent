package com.shoppingagent.auth.dto;

import com.shoppingagent.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
