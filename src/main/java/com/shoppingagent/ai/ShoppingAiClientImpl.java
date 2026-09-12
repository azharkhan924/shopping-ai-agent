package com.shoppingagent.ai;

import com.shoppingagent.ai.exception.AiServiceException;
import com.shoppingagent.ai.exception.MalformedAiResponseException;
import com.shoppingagent.ai.model.RequirementAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ShoppingAiClientImpl implements ShoppingAiClient {

    private static final Logger log = LoggerFactory.getLogger(ShoppingAiClientImpl.class);

    private final ChatClient chatClient;
    private final ShoppingAiProperties properties;

    public ShoppingAiClientImpl(ChatClient shoppingChatClient, ShoppingAiProperties properties) {
        this.chatClient = shoppingChatClient;
        this.properties = properties;
    }

    @Override
    public RequirementAnalysis extractRequirement(String systemContext, String userPrompt) {
        try {
            CompletableFuture<RequirementAnalysis> future = CompletableFuture.supplyAsync(() ->
                    chatClient.prompt()
                            .system(systemContext == null ? "" : systemContext)
                            .user(userPrompt)
                            .call()
                            .entity(RequirementAnalysis.class)
            );

            RequirementAnalysis result = future.get(properties.getTimeoutMillis(), TimeUnit.MILLISECONDS);

            if (result == null) {
                throw new MalformedAiResponseException("AI returned an empty/unparseable response");
            }
            return result;

        } catch (TimeoutException e) {
            log.warn("AI call timed out after {}ms", properties.getTimeoutMillis());
            throw new AiServiceException("The AI shopping assistant timed out. Please try again.", e);
        } catch (MalformedAiResponseException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI call failed", e);
            throw new AiServiceException("The AI shopping assistant is currently unavailable.", e);
        }
    }
}
