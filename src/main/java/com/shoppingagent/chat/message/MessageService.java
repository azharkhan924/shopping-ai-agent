package com.shoppingagent.chat.message;

import com.shoppingagent.chat.conversation.Conversation;
import com.shoppingagent.chat.conversation.ConversationService;
import com.shoppingagent.chat.message.dto.MessageCreateRequest;
import com.shoppingagent.chat.message.dto.MessageResponse;
import com.shoppingagent.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Stores/retrieves chat messages. This is intentionally "dumb" for now:
 * it only persists whatever content it is given. The future AI agent
 * (Module 4+) will call this service to save the user's message and,
 * separately, to save the assistant's reply once it has one - this
 * class should never need to know how that reply was produced.
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;

    public MessageService(MessageRepository messageRepository, ConversationService conversationService) {
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
    }

    @Transactional
    public MessageResponse createUserMessage(User user, UUID conversationId, MessageCreateRequest request) {
        Conversation conversation = conversationService.getOwnedConversation(user, conversationId);

        Message message = Message.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(request.content())
                .build();

        Message saved = messageRepository.save(message);
        return toResponse(saved);
    }

    public List<MessageResponse> listMessages(User user, UUID conversationId) {
        Conversation conversation = conversationService.getOwnedConversation(user, conversationId);

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
