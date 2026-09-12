package com.shoppingagent.chat.message;

import com.shoppingagent.chat.message.dto.MessageCreateRequest;
import com.shoppingagent.chat.message.dto.MessageResponse;
import com.shoppingagent.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID conversationId,
                                                    @Valid @RequestBody MessageCreateRequest request) {
        MessageResponse response = messageService.createUserMessage(principal.getUser(), conversationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable UUID conversationId) {
        return ResponseEntity.ok(messageService.listMessages(principal.getUser(), conversationId));
    }
}
