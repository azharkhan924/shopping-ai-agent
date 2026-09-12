package com.shoppingagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingagent.agent.dto.ChatRequest;
import com.shoppingagent.agent.dto.ChatResponse;
import com.shoppingagent.security.UserPrincipal;
import com.shoppingagent.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ShoppingAgentPipelineService pipelineService;

    private ChatController chatController;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        chatController = new ChatController(pipelineService, objectMapper);
    }

    @Test
    void chatReturnsSuccessfulResponse() {
        UUID conversationId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com").build();
        UserPrincipal principal = new UserPrincipal(user);

        ChatRequest request = new ChatRequest(conversationId, "Find me shoes");
        ChatResponse mockResponse = ChatResponse.builder()
                .conversationId(conversationId.toString())
                .message("Here are top shoes")
                .products(List.of())
                .build();

        when(pipelineService.process(eq(user), eq(conversationId), eq("Find me shoes"))).thenReturn(mockResponse);

        ResponseEntity<ChatResponse> result = chatController.chat(principal, request);

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals("Here are top shoes", result.getBody().getMessage());
    }

    @Test
    void streamReturnsNonNullSseEmitter() {
        UUID conversationId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com").build();
        UserPrincipal principal = new UserPrincipal(user);

        ChatRequest request = new ChatRequest(conversationId, "Find me shoes");

        var emitter = chatController.stream(principal, request);
        assertNotNull(emitter);
    }
}
