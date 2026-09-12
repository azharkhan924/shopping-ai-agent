package com.shoppingagent.search.exception;

/**
 * Thrown when the search layer as a whole cannot produce results (e.g. every
 * provider failed). Individual provider failures are captured in
 * {@code ProviderSearchResult} and do NOT throw — this is only for the
 * "nothing worked at all" case.
 */
public class SearchException extends RuntimeException {

    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
