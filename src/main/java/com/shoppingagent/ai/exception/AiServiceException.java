package com.shoppingagent.ai.exception;

/**
 * Thrown when the AI provider is unavailable, times out, or otherwise fails to
 * produce a usable response. Callers should catch this and return a clean
 * user-facing error rather than propagating internals.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
