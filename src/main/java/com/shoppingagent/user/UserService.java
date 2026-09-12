package com.shoppingagent.user;

import com.shoppingagent.exception.ResourceNotFoundException;
import com.shoppingagent.user.dto.UserResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Plain user lookups/mapping. Registration/login *flow* (hashing,
 * credential checks, JWT issuing) lives in the auth module - this
 * service just owns the User entity itself so other modules
 * (conversation, message, and later modules) can depend on it
 * without depending on auth internals.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
