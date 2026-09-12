package com.shoppingagent.agent;

import com.shoppingagent.agent.dto.ChatResponse;
import com.shoppingagent.ai.ChatTurn;
import com.shoppingagent.ai.ShoppingAgentService;
import com.shoppingagent.ai.exception.AiServiceException;
import com.shoppingagent.ai.intent.ShoppingIntent;
import com.shoppingagent.ai.model.RequirementAnalysis;
import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.chat.conversation.Conversation;
import com.shoppingagent.chat.conversation.ConversationService;
import com.shoppingagent.chat.message.Message;
import com.shoppingagent.chat.message.MessageRepository;
import com.shoppingagent.chat.message.MessageRole;
import com.shoppingagent.product.comparison.ComparisonResult;
import com.shoppingagent.product.comparison.ProductComparisonService;
import com.shoppingagent.product.grouping.ProductGroup;
import com.shoppingagent.product.grouping.ProductGroupingService;
import com.shoppingagent.product.normalization.ProductNormalizationService;
import com.shoppingagent.product.ranking.BadgeAssignmentService;
import com.shoppingagent.product.ranking.ProductRankingService;
import com.shoppingagent.search.ProductSearchService;
import com.shoppingagent.search.exception.SearchException;
import com.shoppingagent.search.model.Product;
import com.shoppingagent.search.model.SearchResult;
import com.shoppingagent.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module 8 — Complete Shopping Agent Pipeline.
 * Orchestrates: AI → Search → Normalize → Group → Compare → Rank → Badge → Response
 * 
 * This service coordinates all sub-services but does NOT contain the algorithms itself.
 */
@Service
public class ShoppingAgentPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingAgentPipelineService.class);

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final ShoppingAgentService shoppingAgentService;
    private final ProductSearchService productSearchService;
    private final ProductNormalizationService normalizationService;
    private final ProductGroupingService groupingService;
    private final ProductComparisonService comparisonService;
    private final ProductRankingService rankingService;
    private final BadgeAssignmentService badgeAssignmentService;

    // In-memory cache for ShoppingQuery per conversation (for refinement support)
    private final ConcurrentHashMap<UUID, ShoppingQuery> queryCache = new ConcurrentHashMap<>();
    // In-memory cache for last product groups per conversation (for comparison)
    private final ConcurrentHashMap<UUID, List<ProductGroup>> resultCache = new ConcurrentHashMap<>();

    public ShoppingAgentPipelineService(
            ConversationService conversationService,
            MessageRepository messageRepository,
            ShoppingAgentService shoppingAgentService,
            ProductSearchService productSearchService,
            ProductNormalizationService normalizationService,
            ProductGroupingService groupingService,
            ProductComparisonService comparisonService,
            ProductRankingService rankingService,
            BadgeAssignmentService badgeAssignmentService) {
        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
        this.shoppingAgentService = shoppingAgentService;
        this.productSearchService = productSearchService;
        this.normalizationService = normalizationService;
        this.groupingService = groupingService;
        this.comparisonService = comparisonService;
        this.rankingService = rankingService;
        this.badgeAssignmentService = badgeAssignmentService;
    }

    /**
     * Process a user message through the full pipeline (synchronous).
     */
    @Transactional
    public ChatResponse process(User user, UUID conversationId, String userMessage) {
        return processInternal(user, conversationId, userMessage, stage -> {});
    }

    /**
     * Process with SSE progress events.
     */
    @Transactional
    public ChatResponse processWithProgress(User user, UUID conversationId, String userMessage,
                                             Consumer<AgentStage> progressCallback) {
        return processInternal(user, conversationId, userMessage, progressCallback);
    }

    private ChatResponse processInternal(User user, UUID conversationId, String userMessage,
                                          Consumer<AgentStage> progressCallback) {
        long startTime = System.currentTimeMillis();

        // Step 1: Verify conversation ownership
        Conversation conversation = conversationService.getOwnedConversation(user, conversationId);

        // Step 2: Save USER message
        saveMessage(conversation, MessageRole.USER, userMessage);

        // Step 3: Load conversation context
        List<ChatTurn> history = loadHistory(conversationId);
        ShoppingQuery previousQuery = queryCache.get(conversationId);

        // Step 4: AI Analysis
        progressCallback.accept(AgentStage.ANALYZING);
        RequirementAnalysis analysis;
        try {
            analysis = shoppingAgentService.analyze(history, previousQuery, userMessage);
        } catch (AiServiceException e) {
            log.warn("AI analysis failed for conversation {}, falling back to keyword heuristic: {}", conversationId, e.getMessage());
            analysis = fallbackExtractRequirement(userMessage);
            if (analysis == null) {
                String errorMsg = "I couldn't understand that request right now. Please try again.";
                saveMessage(conversation, MessageRole.ASSISTANT, errorMsg);
                return buildResponse(conversationId, "ERROR", errorMsg, null, null);
            }
        }

        // Step 5: Handle intent
        ShoppingIntent intent = analysis.getIntent();
        log.debug("Conversation {}: intent={}, readyToSearch={}", conversationId, intent, analysis.isReadyToSearch());

        // Handle GENERAL_SHOPPING_QUERY / GENERAL_CONVERSATION
        if (intent == ShoppingIntent.GENERAL_SHOPPING_QUERY) {
            String msg = analysis.getClarificationQuestion() != null
                    ? analysis.getClarificationQuestion()
                    : "I'm your AI shopping assistant! Tell me what you're looking for — a product category, budget, or brand — and I'll find the best options for you.";
            saveMessage(conversation, MessageRole.ASSISTANT, msg);
            return buildResponse(conversationId, "GENERAL_CONVERSATION", msg, null, null);
        }

        // Handle PRODUCT_COMPARISON using cached results
        if (intent == ShoppingIntent.PRODUCT_COMPARISON) {
            List<ProductGroup> cachedResults = resultCache.get(conversationId);
            if (cachedResults != null && cachedResults.size() >= 2) {
                progressCallback.accept(AgentStage.COMPARING);
                List<ProductGroup> toCompare = cachedResults.subList(0, Math.min(cachedResults.size(), 3));
                ComparisonResult comparison = comparisonService.compare(toCompare);
                String msg = comparison.getSummary();
                saveMessage(conversation, MessageRole.ASSISTANT, msg);
                return buildResponse(conversationId, "PRODUCT_COMPARISON", msg, toCompare, comparison);
            } else {
                String msg = "I don't have products to compare yet. Tell me what you're looking for first!";
                saveMessage(conversation, MessageRole.ASSISTANT, msg);
                return buildResponse(conversationId, "CLARIFICATION", msg, null, null);
            }
        }

        // Step 6: Clarification check
        if (!analysis.isReadyToSearch()) {
            String question = analysis.getClarificationQuestion() != null
                    ? analysis.getClarificationQuestion()
                    : "Could you share a bit more detail about what you're looking for?";
            // Cache the partial query for refinement
            if (analysis.getShoppingQuery() != null) {
                queryCache.put(conversationId, analysis.getShoppingQuery());
            }
            saveMessage(conversation, MessageRole.ASSISTANT, question);
            return buildResponse(conversationId, "CLARIFICATION", question, null, null);
        }

        // Step 7: Ready to search — cache query for future refinement
        ShoppingQuery query = analysis.getShoppingQuery();
        queryCache.put(conversationId, query);

        // Step 8: Product Search
        progressCallback.accept(AgentStage.SEARCHING);
        SearchResult searchResult;
        try {
            searchResult = productSearchService.search(query);
        } catch (SearchException e) {
            log.warn("Search failed for conversation {}: {}", conversationId, e.getMessage());
            String errorMsg = "I couldn't search for products right now. Please try again.";
            saveMessage(conversation, MessageRole.ASSISTANT, errorMsg);
            return buildResponse(conversationId, "ERROR", errorMsg, null, null);
        }

        List<Product> products = searchResult.getProducts();
        // If no products were found and maxPrice was inherited from previous conversation turn,
        // retry search without the inherited price ceiling (e.g. user asked for a MacBook Air M3 after previously searching under 40k)
        if (products.isEmpty() && query.getMaxPrice() != null && previousQuery != null && previousQuery.getMaxPrice() != null) {
            log.info("Zero products found with inherited budget ₹{}. Retrying search without inherited price ceiling...", query.getMaxPrice());
            ShoppingQuery budgetRelaxed = query.toBuilder().maxPrice(null).minPrice(null).build();
            try {
                SearchResult rescued = productSearchService.search(budgetRelaxed);
                if (!rescued.getProducts().isEmpty()) {
                    log.info("Rescued {} products by clearing inherited budget ceiling", rescued.getProducts().size());
                    searchResult = rescued;
                    products = searchResult.getProducts();
                    query = budgetRelaxed;
                    queryCache.put(conversationId, budgetRelaxed);
                }
            } catch (Exception ignored) {}
        }

        if (products.isEmpty()) {
            String msg = "I couldn't find anything matching that. Want to try a different budget or brand?";
            saveMessage(conversation, MessageRole.ASSISTANT, msg);
            return buildResponse(conversationId, "NO_RESULTS", msg, null, null);
        }

        // Step 9: Normalize
        progressCallback.accept(AgentStage.NORMALIZING);
        List<Product> normalized = normalizationService.normalize(products);

        // Step 10: Group (dedup + multi-store grouping)
        List<ProductGroup> groups = groupingService.group(normalized);

        // Step 11: Compare & Rank
        progressCallback.accept(AgentStage.COMPARING);
        progressCallback.accept(AgentStage.RANKING);
        List<ProductGroup> ranked = rankingService.rank(groups, query);

        // Step 12: Assign badges
        badgeAssignmentService.assignBadges(ranked);

        // Cache results for future comparison requests
        resultCache.put(conversationId, ranked);

        // Step 13: Generate explanation
        progressCallback.accept(AgentStage.GENERATING_RESPONSE);
        String explanation = generateExplanation(ranked, query);

        // Step 14: Save ASSISTANT message
        saveMessage(conversation, MessageRole.ASSISTANT, explanation);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Pipeline completed for conversation {} in {}ms. Found {} groups.",
                conversationId, elapsed, ranked.size());

        // Step 15: Return structured response
        return buildResponse(conversationId, "PRODUCT_RESULTS", explanation, ranked, null);
    }

    /**
     * Generate a concise, factual explanation using real backend data only.
     * No LLM involved — uses templates over actual data.
     */
    private String generateExplanation(List<ProductGroup> ranked, ShoppingQuery query) {
        if (ranked.isEmpty()) {
            return "I couldn't find any products matching your requirements.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("I found ").append(ranked.size())
                .append(ranked.size() == 1 ? " option" : " options")
                .append(" for you");

        if (query != null && query.getCategory() != null) {
            sb.append(" in **").append(query.getCategory()).append("**");
        }
        sb.append(".\n\n");

        for (int i = 0; i < Math.min(ranked.size(), 5); i++) {
            ProductGroup g = ranked.get(i);
            sb.append("**").append(i + 1).append(". ").append(g.getName()).append("**");
            if (g.getBadge() != null) {
                sb.append(" — ").append(formatBadge(g.getBadge()));
            }
            sb.append("\n");
            if (g.getBestPrice() != null) {
                sb.append("₹").append(String.format("%.0f", g.getBestPrice()));
            }
            if (g.getRating() != null) {
                sb.append(" · ").append(g.getRating()).append("★");
            }
            if (g.getOffers() != null && g.getOffers().size() > 1) {
                sb.append(" · Available at ").append(g.getOffers().size()).append(" stores");
            } else if (g.getOffers() != null && g.getOffers().size() == 1) {
                sb.append(" · ").append(g.getOffers().get(0).getStore());
            }
            if (g.getReason() != null) {
                sb.append("\n").append(g.getReason());
            }
            sb.append("\n\n");
        }

        // Recommendation
        if (!ranked.isEmpty()) {
            ProductGroup top = ranked.get(0);
            sb.append("**My pick:** ").append(top.getName())
                    .append(" — best overall match for your requirements.");
        }

        return sb.toString().trim();
    }

    private String formatBadge(String badge) {
        return switch (badge) {
            case "BEST_OVERALL" -> "🏆 Best Overall";
            case "CHEAPEST" -> "💰 Cheapest";
            case "BEST_VALUE" -> "⭐ Best Value";
            case "HIGHEST_RATED" -> "⭐ Highest Rated";
            case "BEST_BATTERY" -> "🔋 Best Battery";
            case "MOST_POPULAR" -> "🔥 Most Popular";
            default -> badge;
        };
    }

    private List<ChatTurn> loadHistory(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(m -> new ChatTurn(m.getRole().name(), m.getContent()))
                .toList();
    }

    private void saveMessage(Conversation conversation, MessageRole role, String content) {
        Message message = Message.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .build();
        messageRepository.save(message);
    }

    private ChatResponse buildResponse(String conversationId, String status, String message,
                                        List<ProductGroup> groups, ComparisonResult comparison) {
        List<ChatResponse.ProductGroupDto> productDtos = groups == null ? null :
                groups.stream().map(ChatResponse::toDto).toList();

        return ChatResponse.builder()
                .conversationId(conversationId)
                .status(status)
                .message(message)
                .products(productDtos)
                .comparison(comparison)
                .build();
    }

    private ChatResponse buildResponse(UUID conversationId, String status, String message,
                                        List<ProductGroup> groups, ComparisonResult comparison) {
        return buildResponse(conversationId.toString(), status, message, groups, comparison);
    }

    RequirementAnalysis fallbackExtractRequirement(String message) {
        if (message == null || message.isBlank()) return null;
        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.matches("^(hi|hello|hey|greetings|howdy|hola)(\\s+.*)?$") && lower.length() < 15) {
            return RequirementAnalysis.builder()
                    .intent(ShoppingIntent.GENERAL_SHOPPING_QUERY)
                    .readyToSearch(false)
                    .clarificationQuestion("Hello! I'm Penny, your AI shopping assistant. Tell me what product you're looking for, your budget, or preferred brand!")
                    .build();
        }

        String category = "electronics";
        if (lower.contains("pendrive") || lower.contains("pen drive") || lower.contains("flash drive") || lower.contains("usb")) {
            category = "pendrive";
        } else if (lower.contains("headphone") || lower.contains("earphone") || lower.contains("earbud") || lower.contains("airpod")) {
            category = "headphones";
        } else if (lower.contains("laptop") || lower.contains("notebook") || lower.contains("macbook") || lower.contains("ultrabook")) {
            category = "laptop";
        } else if (lower.contains("phone") || lower.contains("mobile") || lower.contains("smartphone") || lower.contains("iphone")) {
            category = "phone";
        } else if (lower.contains("watch") || lower.contains("smartwatch")) {
            category = "smartwatch";
        } else if (lower.contains("shoe") || lower.contains("sneaker") || lower.contains("footwear") || lower.contains("running")) {
            category = "shoes";
        } else if (lower.contains("mouse") || lower.contains("mice")) {
            category = "mouse";
        } else if (lower.contains("keyboard")) {
            category = "keyboard";
        } else if (lower.contains("tablet") || lower.contains("ipad")) {
            category = "tablet";
        }

        Double maxPrice = null;
        Matcher priceMatcher = Pattern.compile("(?:under|below|budget|less than|rs\\.?|inr|₹)\\s*(\\d+)").matcher(lower);
        if (priceMatcher.find()) {
            try {
                maxPrice = Double.parseDouble(priceMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        ShoppingQuery query = ShoppingQuery.builder()
                .category(category)
                .keywords(List.of(lower.split("\\s+")))
                .maxPrice(maxPrice)
                .build();

        return RequirementAnalysis.builder()
                .intent(ShoppingIntent.PRODUCT_SEARCH)
                .readyToSearch(true)
                .shoppingQuery(query)
                .build();
    }

    /**
     * Pipeline stages for SSE progress events.
     */
    public enum AgentStage {
        ANALYZING("Understanding your requirements..."),
        SEARCHING("Searching available products..."),
        NORMALIZING("Processing product data..."),
        COMPARING("Comparing prices and features..."),
        RANKING("Finding the best options..."),
        GENERATING_RESPONSE("Preparing your recommendations...");

        private final String displayMessage;

        AgentStage(String displayMessage) {
            this.displayMessage = displayMessage;
        }

        public String getDisplayMessage() {
            return displayMessage;
        }
    }
}
