package com.shoppingagent.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.model.Product;
import com.shoppingagent.search.provider.LiveProductSearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveProductSearchProviderTest {

    @Mock
    private ChatClient chatClient;

    private LiveProductSearchProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        provider = new LiveProductSearchProvider(chatClient, objectMapper);
    }

    @Test
    void providerName_isLiveAi() {
        assertThat(provider.getProviderName()).isEqualTo("LIVE_AI");
    }

    @Test
    void fallbacksToMock_whenAiThrowsException() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("API connection timeout"));

        ShoppingQuery query = ShoppingQuery.builder().category("laptop").build();
        List<Product> products = provider.search(query);

        assertThat(products).isNotEmpty();
        assertThat(products).allMatch(p -> p.getCategory().equalsIgnoreCase("laptop"));
    }

    @Test
    void imageResolver_returnsValidUrlsForCategories() {
        String laptopImg = LiveProductSearchProvider.resolveImageUrl("laptop", "Lenovo IdeaPad");
        String watchImg = LiveProductSearchProvider.resolveImageUrl("smartwatch", "boAt Wave Call");
        String shoeImg = LiveProductSearchProvider.resolveImageUrl("shoes", "Nike Revolution");

        assertThat(laptopImg).startsWith("https://images.unsplash.com");
        assertThat(watchImg).startsWith("https://images.unsplash.com");
        assertThat(shoeImg).startsWith("https://images.unsplash.com");
    }
}
