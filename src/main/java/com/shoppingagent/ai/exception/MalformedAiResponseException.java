package com.shoppingagent.ai.exception;

/**
 * Thrown when the LLM returns output that cannot be parsed/validated into a
 * {@code RequirementAnalysis}. This is a subtype of AiServiceException so callers
 * can handle "AI is broken" uniformly, while still distinguishing malformed output
 * from outright unavailability/timeouts in logs.
 */
public class MalformedAiResponseException extends AiServiceException {

    public MalformedAiResponseException(String message) {
        super(message);
    }

    public MalformedAiResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
