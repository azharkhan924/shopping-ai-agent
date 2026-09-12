package com.shoppingagent.chat.conversation;

import com.shoppingagent.chat.conversation.dto.ConversationCreateRequest;
import com.shoppingagent.chat.conversation.dto.ConversationResponse;
import com.shoppingagent.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                         @Valid @RequestBody(required = false) ConversationCreateRequest request) {
        ConversationResponse response = conversationService.createConversation(principal.getUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(conversationService.listConversations(principal.getUser()));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> get(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable UUID conversationId) {
        return ResponseEntity.ok(conversationService.getConversation(principal.getUser(), conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID conversationId) {
        conversationService.deleteConversation(principal.getUser(), conversationId);
        return ResponseEntity.noContent().build();
    }
}
