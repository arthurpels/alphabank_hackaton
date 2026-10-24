package com.alphabank.backend.enrichment.client;

public class MockClientException extends RuntimeException {

    public MockClientException(String message) {
        super(message);
    }

    public MockClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
