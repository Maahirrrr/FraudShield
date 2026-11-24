package com.fraudshield.exception;

/**
 * Thrown when the remote Python AI service times out or cannot be reached.
 */
public class ModelServiceUnavailableException extends ScoringException {
    public ModelServiceUnavailableException(String message) {
        super(message);
    }
    public ModelServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
