package com.gamemarket.summary;

/** Summary could not be produced (LLM error, quota, bad output); mapped to HTTP 503. */
public class SummaryUnavailableException extends RuntimeException {

    public SummaryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public SummaryUnavailableException(String message) {
        super(message);
    }
}
