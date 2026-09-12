package com.shoppingagent.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Spring AI {@link ChatClient}. The concrete provider (model, API key)
 * comes entirely from Spring AI's own auto-configuration, driven by the
 * AI_API_KEY / AI_MODEL environment variables in application.yml — nothing is
 * hardcoded here. Swapping providers (OpenAI -> Azure -> Anthropic, etc.) only
 * requires changing the Spring AI starter dependency in pom.xml plus the
 * corresponding spring.ai.* properties; this class and everything downstream
 * of it is unaffected.
 */
@Configuration
@EnableConfigurationProperties(ShoppingAiProperties.class)
public class ShoppingAiConfig {

    @Bean
    public ChatClient shoppingChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultSystem("""
                        You are the reasoning component of an Indian e-commerce shopping assistant.
                        You NEVER invent product facts (price, rating, availability, specs, URLs) —
                        you only understand user intent and produce structured requirements.
                        Always respond with a single JSON object matching the requested schema and
                        nothing else: no prose, no markdown fences, no explanations.
                        """)
                .build();
    }
}
