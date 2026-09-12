package com.shoppingagent.chat.conversation;

import com.shoppingagent.chat.conversation.dto.ConversationCreateRequest;
import com.shoppingagent.chat.conversation.dto.ConversationResponse;
import com.shoppingagent.exception.ForbiddenException;
import com.shoppingagent.exception.ResourceNotFoundException;
import com.shoppingagent.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns conversation CRUD + the "does this conversation belong to this
 * user" check. Every future module that touches conversations (the AI
 * agent, product search, etc.) should go through here rather than
 * hitting the repository directly, so this ownership rule stays in
 * exactly one place.
 */
@Service
public class ConversationService {

    private static final String DEFAULT_TITLE = "New Shopping Conversation";

    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public ConversationResponse createConversation(User user, ConversationCreateRequest request) {
        String title = (request == null || request.title() == null || request.title().isBlank())
                ? DEFAULT_TITLE
                : request.title().trim();

        Conversation conversation = Conversation.builder()
                .user(user)
                .title(title)
                .build();

        Conversation saved = conversationRepository.save(conversation);
        return toResponse(saved);
    }

    public List<ConversationResponse> listConversations(User user) {
        return conversationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ConversationResponse getConversation(User user, UUID conversationId) {
        return toResponse(getOwnedConversation(user, conversationId));
    }

    @Transactional
    public void deleteConversation(User user, UUID conversationId) {
        Conversation conversation = getOwnedConversation(user, conversationId);
        conversationRepository.delete(conversation);
    }

    /**
     * Fetches a conversation and enforces ownership. Used by this
     * service and by the message module, which needs the same check
     * before it can touch a conversation's messages.
     */
    public Conversation getOwnedConversation(User user, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You do not have access to this conversation");
        }

        return conversation;
    }

    private ConversationResponse toResponse(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}
