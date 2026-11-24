package com.fraudshield.exception;

public class ScoringException extends Exception {
    public ScoringException(String message) {
        super(message);
    }
    public ScoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
