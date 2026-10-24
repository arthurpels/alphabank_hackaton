package com.alphabank.backend.support;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@Component
public class LlmCircuitBreaker {

    private final int failureThreshold;
    private final long openMs;
    private final Clock clock;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    public LlmCircuitBreaker(@Value("${app.llm.circuit-failures}") int failureThreshold,
                             @Value("${app.llm.circuit-open-ms}") long openMs, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openMs = openMs;
        this.clock = clock;
    }

    public <T> T execute(Supplier<T> call) {
        if (openUntil.get() > clock.millis()) {
            throw new ResourceAccessException("LLM временно недоступна: circuit breaker разомкнут");
        }
        try {
            T result = call.get();
            reset();
            return result;
        } catch (RestClientException e) {
            recordFailure();
            throw e;
        }
    }

    private synchronized void recordFailure() {
        if (failures.incrementAndGet() >= failureThreshold) {
            openUntil.set(clock.millis() + openMs);
        }
    }

    private synchronized void reset() {
        failures.set(0);
        openUntil.set(0);
    }
}
