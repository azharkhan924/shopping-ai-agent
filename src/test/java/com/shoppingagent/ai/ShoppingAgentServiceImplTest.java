package com.shoppingagent.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingagent.ai.exception.MalformedAiResponseException;
import com.shoppingagent.ai.intent.ShoppingIntent;
import com.shoppingagent.ai.model.RequirementAnalysis;
import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.ai.model.SortPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShoppingAgentServiceImplTest {

    private ShoppingAiClient shoppingAiClient;
    private ShoppingAgentServiceImpl service;

    @BeforeEach
    void setUp() {
        shoppingAiClient = mock(ShoppingAiClient.class);
        ShoppingAiProperties properties = new ShoppingAiProperties();
        properties.setMaxContextTurns(4);
        service = new ShoppingAgentServiceImpl(shoppingAiClient, properties, new ObjectMapper());
    }

    @Test
    void simpleProductQuery_isReadyToSearch() {
        RequirementAnalysis mocked = RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .shoppingQuery(ShoppingQuery.builder().category("headphones").build())
                .readyToSearch(true)
                .missingInformation(List.of())
                .build();
        when(shoppingAiClient.extractRequirement(anyString(), anyString())).thenReturn(mocked);

        RequirementAnalysis result = service.analyze(List.of(), null, "Find headphones");

        assertThat(result.isReadyToSearch()).isTrue();
        assertThat(result.getIntent()).isEqualTo(ShoppingIntent.PRODUCT_SEARCH);
    }

    @Test
    void queryWithBudget_isIncludedInPromptAndResult() {
        RequirementAnalysis mocked = RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .shoppingQuery(ShoppingQuery.builder()
                        .category("headphones")
                        .maxPrice(3000.0)
                        .currency("INR")
                        .build())
                .readyToSearch(true)
                .build();
        when(shoppingAiClient.extractRequirement(anyString(), anyString())).thenReturn(mocked);

        RequirementAnalysis result = service.analyze(List.of(), null, "Find wireless headphones under ₹3000");

        assertThat(result.getShoppingQuery().getMaxPrice()).isEqualTo(3000.0);
    }

    @Test
    void queryWithBrand_passesThroughBrandField() {
        RequirementAnalysis mocked = RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .shoppingQuery(ShoppingQuery.builder()
                        .category("pendrive")
                        .brands(List.of("SanDisk"))
                        .build())
                .readyToSearch(true)
                .build();
        when(shoppingAiClient.extractRequirement(anyString(), anyString())).thenReturn(mocked);

        RequirementAnalysis result = service.analyze(List.of(), null, "SanDisk pendrive please");

        assertThat(result.getShoppingQuery().getBrands()).containsExactly("SanDisk");
    }

    @Test
    void queryWithMultipleRequirements_allFieldsPreserved() {
        RequirementAnalysis mocked = RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .shoppingQuery(ShoppingQuery.builder()
                        .category("pendrive")
                        .maxPrice(1000.0)
                        .brands(List.of("SanDisk", "Kingston"))
                        .requiredSpecifications(java.util.Map.of("capacity", "128GB"))
                        .sortPreference(SortPreference.BEST_VALUE)
                        .build())
                .readyToSearch(true)
                .build();
        when(shoppingAiClient.extractRequirement(anyString(), anyString())).thenReturn(mocked);

        RequirementAnalysis result = service.analyze(List.of(), null,
                "Mujhe 128GB ka pendrive chahiye achhi brand ka, ₹1000 ke andar.");

        assertThat(result.getShoppingQuery().getCategory()).isEqualTo("pendrive");
        assertThat(result.getShoppingQuery().getMaxPrice()).isEqualTo(1000.0);
        assertThat(result.getShoppingQuery().getRequiredSpecifications()).containsEntry("capacity", "128GB");
    }

    @Test
    void missingInformation_triggersClarification() {
        RequirementAnalysis mocked = RequirementAnalysis.builder()
                .intent(ShoppingIntent.CLARIFICATION_REQUIRED)
                .shoppingQuery(ShoppingQuery.builder().category("headphones").build())
                .readyToSearch(false)
                .missingInformation(List.of("budget"))
                .clarificationQuestion("What's your approximate budget?")
                .build();
        when(shoppingAiClient.extractRequirement(anyString(), anyString())).thenReturn(mocked);

        RequirementAnalysis result = service.analyze(List.of(), null, "I want headphones");

        assertThat(result.isReadyToSearch()).isFalse();
        assertThat(result.getClarificationQuestion()).isNotBlank();
    }

    @Test
    void refinement_mergesWithPreviousQuery_andSendsItInPrompt() {
        ShoppingQuery previous = ShoppingQuery.builder().category("headphones").maxPrice(3000.0).build();
        RequirementAnalysis mocked = RequirementAnalysis.builder()
                .intent(ShoppingIntent.REFINEMENT)
                .shoppingQuery(previous.toBuilder()
                        .userPreferences(java.util.Map.of("battery", "high"))
                        .build())
                .readyToSearch(true)
                .build();
        when(shoppingAiClient.extractRequirement(anyString(), anyString())).thenReturn(mocked);

        service.analyze(List.of(new ChatTurn("USER", "Show me headphones under ₹3000"),
                        new ChatTurn("ASSISTANT", "Sure.")),
                previous, "Battery life is most important.");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(shoppingAiClient).extractRequirement(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("Previous shopping requirement so far");
        assertThat(promptCaptor.getValue()).contains("headphones");
    }

    @Test
    void invalidAiOutput_missingIntent_throwsMalformedException() {
        RequirementAnalysis malformed = RequirementAnalysis.builder()
                .shoppingQuery(ShoppingQuery.builder().build())
                .readyToSearch(true)
                .build();
        when(shoppingAiClient.extractRequirement(anyString(), anyString())).thenReturn(malformed);

        assertThatThrownBy(() -> service.analyze(List.of(), null, "anything"))
                .isInstanceOf(MalformedAiResponseException.class);
    }

    @Test
    void invalidAiOutput_missingShoppingQuery_throwsMalformedException() {
        RequirementAnalysis malformed = RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .readyToSearch(true)
                .build();
        when(shoppingAiClient.extractRequirement(anyString(), anyString())).thenReturn(malformed);

        assertThatThrownBy(() -> service.analyze(List.of(), null, "anything"))
                .isInstanceOf(MalformedAiResponseException.class);
    }

    @Test
    void blankMessage_isRejected() {
        assertThatThrownBy(() -> service.analyze(List.of(), null, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historyLongerThanWindow_isTruncatedToMostRecentTurns() {
        RequirementAnalysis mocked = RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .shoppingQuery(ShoppingQuery.builder().category("pendrive").build())
                .readyToSearch(true)
                .build();
        when(shoppingAiClient.extractRequirement(anyString(), anyString())).thenReturn(mocked);

        List<ChatTurn> longHistory = List.of(
                new ChatTurn("USER", "turn1-should-be-dropped"),
                new ChatTurn("ASSISTANT", "turn2-should-be-dropped"),
                new ChatTurn("USER", "turn3"),
                new ChatTurn("ASSISTANT", "turn4"),
                new ChatTurn("USER", "turn5"),
                new ChatTurn("ASSISTANT", "turn6")
        );

        service.analyze(longHistory, null, "latest message");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(shoppingAiClient).extractRequirement(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("turn1-should-be-dropped");
        assertThat(promptCaptor.getValue()).contains("turn3");
    }
}
