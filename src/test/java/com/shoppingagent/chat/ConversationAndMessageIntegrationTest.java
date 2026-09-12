package com.shoppingagent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingagent.auth.dto.LoginRequest;
import com.shoppingagent.auth.dto.RegisterRequest;
import com.shoppingagent.chat.conversation.dto.ConversationCreateRequest;
import com.shoppingagent.chat.message.dto.MessageCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers conversation + message ownership rules end-to-end: two
 * separate users, JWT-authenticated, verifying user A can never see
 * or touch user B's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationAndMessageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authenticatedUserCanCreateAndListOwnConversations() throws Exception {
        String token = registerAndLogin("owner1@example.com");

        mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ConversationCreateRequest("Headphones"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Headphones"));

        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void conversationWithoutTitleGetsDefaultTitle() throws Exception {
        String token = registerAndLogin("owner2@example.com");

        mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Shopping Conversation"));
    }

    @Test
    void userCannotAccessAnotherUsersConversation() throws Exception {
        String tokenA = registerAndLogin("userA@example.com");
        String tokenB = registerAndLogin("userB@example.com");

        String conversationId = createConversation(tokenA, "User A's chat");

        mockMvc.perform(get("/api/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void conversationDeletionRemovesItsMessages() throws Exception {
        String token = registerAndLogin("deleter@example.com");
        String conversationId = createConversation(token, "To be deleted");

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageCreateRequest("Hello"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Conversation is gone -> 404, and its messages went with it (cascade).
        mockMvc.perform(get("/api/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createMessageStoresAndReturnsIt() throws Exception {
        String token = registerAndLogin("msgsender@example.com");
        String conversationId = createConversation(token, "Pendrive shopping");

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageCreateRequest("128GB pendrive under 1000"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.content").value("128GB pendrive under 1000"));
    }

    @Test
    void messagesAreReturnedInChronologicalOrder() throws Exception {
        String token = registerAndLogin("chrono@example.com");
        String conversationId = createConversation(token, "Ordering test");

        sendMessage(token, conversationId, "first");
        sendMessage(token, conversationId, "second");
        sendMessage(token, conversationId, "third");

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("first"))
                .andExpect(jsonPath("$[1].content").value("second"))
                .andExpect(jsonPath("$[2].content").value("third"));
    }

    @Test
    void emptyMessageContentIsRejected() throws Exception {
        String token = registerAndLogin("emptymsg@example.com");
        String conversationId = createConversation(token, "Validation test");

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageCreateRequest(""))))
                .andExpect(status().isBadRequest());
    }

    private String registerAndLogin(String email) throws Exception {
        RegisterRequest register = new RegisterRequest("Test User", email, "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(email, "password123");
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String createConversation(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ConversationCreateRequest(title))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void sendMessage(String token, String conversationId, String content) throws Exception {
        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageCreateRequest(content))))
                .andExpect(status().isCreated());
    }
}
