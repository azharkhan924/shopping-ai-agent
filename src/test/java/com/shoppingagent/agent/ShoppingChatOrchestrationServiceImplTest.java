package com.shoppingagent.agent;

import com.shoppingagent.ai.ShoppingAgentService;
import com.shoppingagent.ai.exception.AiServiceException;
import com.shoppingagent.ai.intent.ShoppingIntent;
import com.shoppingagent.ai.model.RequirementAnalysis;
import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.ProductSearchService;
import com.shoppingagent.search.exception.SearchException;
import com.shoppingagent.search.model.Product;
import com.shoppingagent.search.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simulates POST /api/conversations/{id}/messages end-to-end at the service
 * layer (Modules 4 + 5 wired together), with the AI and search layers mocked
 * so this test never depends on a real, paid AI API (Module 5.15).
 */
class ShoppingChatOrchestrationServiceImplTest {

    private ShoppingAgentService agentService;
    private ProductSearchService searchService;
    private ShoppingChatOrchestrationServiceImpl orchestration;

    @BeforeEach
    void setUp() {
        agentService = mock(ShoppingAgentService.class);
        searchService = mock(ProductSearchService.class);
        orchestration = new ShoppingChatOrchestrationServiceImpl(agentService, searchService);
    }

    @Test
    void clarificationNeeded_returnsClarificationStatus() {
        when(agentService.analyze(any(), any(), any())).thenReturn(RequirementAnalysis.builder()
                .intent(ShoppingIntent.CLARIFICATION_REQUIRED)
                .shoppingQuery(ShoppingQuery.builder().category("headphones").build())
                .readyToSearch(false)
                .clarificationQuestion("What's your approximate budget?")
                .build());

        AgentResponse response = orchestration.handleUserMessage("conv-1", List.of(), null, "I want headphones");

        assertThat(response.getStatus()).isEqualTo(AgentResponseStatus.CLARIFICATION);
        assertThat(response.getMessage().getContent()).contains("budget");
        assertThat(response.getProducts()).isEmpty();
    }

    @Test
    void readyToSearch_returnsProductResults() {
        ShoppingQuery query = ShoppingQuery.builder().category("pendrive").maxPrice(1000.0).build();
        when(agentService.analyze(any(), any(), any())).thenReturn(RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .shoppingQuery(query)
                .readyToSearch(true)
                .build());

        Product product = Product.builder().id("p1").name("SanDisk 128GB").build();
        when(searchService.search(query)).thenReturn(SearchResult.builder()
                .query(query).products(List.of(product)).totalResults(1).build());

        AgentResponse response = orchestration.handleUserMessage("conv-1", List.of(), null,
                "128GB pendrive under 1000");

        assertThat(response.getStatus()).isEqualTo(AgentResponseStatus.PRODUCT_RESULTS);
        assertThat(response.getProducts()).containsExactly(product);
        assertThat(response.getMessage().getContent()).contains("1 option");
    }

    @Test
    void noResults_returnsNoResultsStatus() {
        ShoppingQuery query = ShoppingQuery.builder().category("pendrive").build();
        when(agentService.analyze(any(), any(), any())).thenReturn(RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .shoppingQuery(query)
                .readyToSearch(true)
                .build());
        when(searchService.search(query)).thenReturn(SearchResult.builder()
                .query(query).products(List.of()).totalResults(0).build());

        AgentResponse response = orchestration.handleUserMessage("conv-1", List.of(), null, "pendrive please");

        assertThat(response.getStatus()).isEqualTo(AgentResponseStatus.NO_RESULTS);
    }

    @Test
    void aiFailure_returnsErrorStatus_withoutLeakingInternals() {
        when(agentService.analyze(any(), any(), any())).thenThrow(new AiServiceException("boom: api key invalid"));

        AgentResponse response = orchestration.handleUserMessage("conv-1", List.of(), null, "anything");

        assertThat(response.getStatus()).isEqualTo(AgentResponseStatus.ERROR);
        assertThat(response.getMessage().getContent()).doesNotContain("api key invalid");
    }

    @Test
    void searchFailure_allProvidersDown_returnsErrorStatus() {
        ShoppingQuery query = ShoppingQuery.builder().category("pendrive").build();
        when(agentService.analyze(any(), any(), any())).thenReturn(RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .shoppingQuery(query)
                .readyToSearch(true)
                .build());
        when(searchService.search(query)).thenThrow(new SearchException(
                "I couldn't search for products right now. Please try again."));

        AgentResponse response = orchestration.handleUserMessage("conv-1", List.of(), null, "pendrive please");

        assertThat(response.getStatus()).isEqualTo(AgentResponseStatus.ERROR);
    }

    @Test
    void comparisonIntent_isNotImplementedYet_returnsClarification() {
        when(agentService.analyze(any(), any(), any())).thenReturn(RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_COMPARISON)
                .shoppingQuery(ShoppingQuery.builder().build())
                .readyToSearch(true)
                .build());

        AgentResponse response = orchestration.handleUserMessage("conv-1", List.of(), null, "compare these two");

        assertThat(response.getStatus()).isEqualTo(AgentResponseStatus.CLARIFICATION);
    }
}
