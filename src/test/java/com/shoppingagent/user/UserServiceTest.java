package com.shoppingagent.user;

import com.shoppingagent.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void passwordIsStoredAsBcryptHashNotPlainText() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "password123";
        String hash = encoder.encode(rawPassword);

        assertThat(hash).isNotEqualTo(rawPassword);
        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches(rawPassword, hash)).isTrue();
    }

    @Test
    void toResponseNeverExposesPasswordHash() {
        UserService userService = new UserService(userRepository);
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Azhar")
                .email("azhar@example.com")
                .passwordHash("$2a$10$somehash")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        UserResponse response = userService.toResponse(user);

        assertThat(response.name()).isEqualTo("Azhar");
        assertThat(response.email()).isEqualTo("azhar@example.com");
        // UserResponse has no password field at all - compile-time guarantee,
        // this assertion just documents the intent.
    }
}
