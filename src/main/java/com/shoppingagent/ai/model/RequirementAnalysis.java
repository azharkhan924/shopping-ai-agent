package com.shoppingagent.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shoppingagent.ai.intent.ShoppingIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of analyzing a user's shopping message (optionally combined with conversation
 * history). This is the structured output the LLM is asked to produce — see
 * {@code ShoppingAiClient}.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequirementAnalysis {

    private ShoppingIntent intent;

    private ShoppingQuery shoppingQuery;

    private boolean readyToSearch;

    @Builder.Default
    private List<String> missingInformation = new ArrayList<>();

    /**
     * Single, minimal clarification question to ask the user. Null/empty when
     * readyToSearch is true.
     */
    private String clarificationQuestion;
}
