package com.alphabank.backend.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

class IoRetryTest {

    private final IoRetry retry = new IoRetry(3, 1);

    @Test
    void retriesIoFailuresAndSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        String result = retry.execute("TEST", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ResourceAccessException("Connection refused");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void givesUpAfterConfiguredAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(ResourceAccessException.class, () -> retry.execute("TEST", () -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("Connection refused");
        }));

        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryClientErrors() {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(HttpClientErrorException.class, () -> retry.execute("TEST", () -> {
            attempts.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }));

        assertEquals(1, attempts.get());
    }

    @Test
    void doesNotRetryServerErrors() {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(HttpServerErrorException.class, () -> retry.execute("TEST", () -> {
            attempts.incrementAndGet();
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        }));

        assertEquals(1, attempts.get());
    }
}
