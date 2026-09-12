package com.shoppingagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingagent.agent.dto.ChatRequest;
import com.shoppingagent.agent.dto.ChatResponse;
import com.shoppingagent.security.UserPrincipal;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Module 8 + 9 — Chat endpoints (synchronous and SSE streaming).
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT = 120_000L; // 2 minutes

    private final ShoppingAgentPipelineService pipelineService;
    private final ObjectMapper objectMapper;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public ChatController(ShoppingAgentPipelineService pipelineService, ObjectMapper objectMapper) {
        this.pipelineService = pipelineService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/chat — Synchronous chat endpoint.
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@AuthenticationPrincipal UserPrincipal principal,
                                              @Valid @RequestBody ChatRequest request) {
        ChatResponse response = pipelineService.process(
                principal.getUser(),
                request.conversationId(),
                request.message()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/chat/stream — SSE streaming endpoint.
     * Emits STATUS events during processing, then RESULT with final data, then DONE.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserPrincipal principal,
                              @Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        sseExecutor.execute(() -> {
            try {
                ChatResponse response = pipelineService.processWithProgress(
                        principal.getUser(),
                        request.conversationId(),
                        request.message(),
                        stage -> sendStatusEvent(emitter, stage)
                );

                // Send final result
                sendResultEvent(emitter, response);

                // Send DONE
                sendDoneEvent(emitter);

                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream error for conversation {}: {}", request.conversationId(), e.getMessage());
                sendErrorEvent(emitter, "I couldn't complete the search right now. Please try again.");
                emitter.complete();
            }
        });

        emitter.onCompletion(() -> log.debug("SSE stream completed for conversation {}", request.conversationId()));
        emitter.onTimeout(() -> {
            log.warn("SSE stream timed out for conversation {}", request.conversationId());
            emitter.complete();
        });
        emitter.onError(e -> log.error("SSE stream error: {}", e.getMessage()));

        return emitter;
    }

    private void sendStatusEvent(SseEmitter emitter, ShoppingAgentPipelineService.AgentStage stage) {
        try {
            Map<String, String> event = new LinkedHashMap<>();
            event.put("type", "STATUS");
            event.put("stage", stage.name());
            event.put("message", stage.getDisplayMessage());

            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(event), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send SSE status event: {}", e.getMessage());
        }
    }

    private void sendResultEvent(SseEmitter emitter, ChatResponse response) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "RESULT");
            event.put("data", response);

            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(event), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send SSE result event: {}", e.getMessage());
        }
    }

    private void sendDoneEvent(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data("{\"type\":\"DONE\"}", MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send SSE done event: {}", e.getMessage());
        }
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            Map<String, String> event = new LinkedHashMap<>();
            event.put("type", "ERROR");
            event.put("message", message);

            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(event), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send SSE error event: {}", e.getMessage());
        }
    }
}
