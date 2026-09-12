package com.shoppingagent.agent;

import com.shoppingagent.agent.dto.ChatResponse;
import com.shoppingagent.ai.ShoppingAgentService;
import com.shoppingagent.ai.intent.ShoppingIntent;
import com.shoppingagent.ai.model.RequirementAnalysis;
import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.chat.conversation.Conversation;
import com.shoppingagent.chat.conversation.ConversationService;
import com.shoppingagent.chat.message.MessageRepository;
import com.shoppingagent.product.comparison.ProductComparisonService;
import com.shoppingagent.product.grouping.ProductGroup;
import com.shoppingagent.product.grouping.ProductGroupingService;
import com.shoppingagent.product.normalization.ProductNormalizationService;
import com.shoppingagent.product.ranking.BadgeAssignmentService;
import com.shoppingagent.product.ranking.ProductRankingService;
import com.shoppingagent.search.ProductSearchService;
import com.shoppingagent.search.model.Product;
import com.shoppingagent.search.model.SearchResult;
import com.shoppingagent.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingAgentPipelineServiceTest {

    @Mock
    private ConversationService conversationService;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ShoppingAgentService shoppingAgentService;
    @Mock
    private ProductSearchService productSearchService;
    @Mock
    private ProductNormalizationService normalizationService;
    @Mock
    private ProductGroupingService groupingService;
    @Mock
    private ProductComparisonService comparisonService;
    @Mock
    private ProductRankingService rankingService;
    @Mock
    private BadgeAssignmentService badgeAssignmentService;

    private ShoppingAgentPipelineService pipelineService;

    @BeforeEach
    void setUp() {
        pipelineService = new ShoppingAgentPipelineService(
                conversationService,
                messageRepository,
                shoppingAgentService,
                productSearchService,
                normalizationService,
                groupingService,
                comparisonService,
                rankingService,
                badgeAssignmentService
        );
    }

    @Test
    void processesSearchIntentSuccessfully() {
        UUID conversationId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
        Conversation conv = Conversation.builder().id(conversationId).user(user).title("Test Conv").build();

        when(conversationService.getOwnedConversation(user, conversationId)).thenReturn(conv);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(List.of());

        ShoppingQuery query = ShoppingQuery.builder().category("headphones").build();
        RequirementAnalysis analysis = RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .readyToSearch(true)
                .shoppingQuery(query)
                .build();
        when(shoppingAgentService.analyze(anyList(), any(), anyString())).thenReturn(analysis);

        Product p = Product.builder().name("Boat Rockerz").price(BigDecimal.valueOf(1299)).build();
        when(productSearchService.search(any())).thenReturn(SearchResult.builder().products(List.of(p)).totalResults(1).build());
        when(normalizationService.normalize(anyList())).thenReturn(List.of(p));

        ProductGroup pg = ProductGroup.builder().name("Boat Rockerz").bestPrice(1299.0).build();
        when(groupingService.group(anyList())).thenReturn(List.of(pg));
        when(rankingService.rank(anyList(), any())).thenReturn(List.of(pg));

        List<ShoppingAgentPipelineService.AgentStage> stages = new ArrayList<>();
        ChatResponse response = pipelineService.processWithProgress(user, conversationId, "Find me wireless headphones", stages::add);

        assertNotNull(response);
        assertEquals(conversationId.toString(), response.getConversationId());
        assertFalse(response.getProducts().isEmpty());
        assertFalse(stages.isEmpty());
        verify(badgeAssignmentService).assignBadges(anyList());
        verify(messageRepository, atLeastOnce()).save(any());
    }

    @Test
    void asksClarificationWhenNotReadyToSearch() {
        UUID conversationId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
        Conversation conv = Conversation.builder().id(conversationId).user(user).title("Test Conv").build();

        when(conversationService.getOwnedConversation(user, conversationId)).thenReturn(conv);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(List.of());

        RequirementAnalysis analysis = RequirementAnalysis.builder()
                .intent(ShoppingIntent.CLARIFICATION_REQUIRED)
                .readyToSearch(false)
                .clarificationQuestion("What is your budget?")
                .build();
        when(shoppingAgentService.analyze(anyList(), any(), anyString())).thenReturn(analysis);

        ChatResponse response = pipelineService.process(user, conversationId, "I want something good");

        assertNotNull(response);
        assertEquals("What is your budget?", response.getMessage());
        assertNull(response.getProducts());
    }
}
