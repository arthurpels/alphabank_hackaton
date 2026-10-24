package com.alphabank.backend.support;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Component
public class IoRetry {

    private static final Logger log = LoggerFactory.getLogger(IoRetry.class);

    private final int attempts;
    private final long backoffMs;

    public IoRetry(@Value("${app.external.retry-attempts}") int attempts,
                   @Value("${app.external.retry-backoff-ms}") long backoffMs) {
        this.attempts = attempts;
        this.backoffMs = backoffMs;
    }

    public <T> T execute(String source, Supplier<T> call) {
        int attempt = 1;
        while (true) {
            try {
                return call.get();
            } catch (ResourceAccessException e) {
                if (attempt >= attempts) {
                    log.warn("Источник {} недоступен после {} попыток: {}", source, attempt, e.getMessage());
                    throw e;
                }
                log.warn("Источник {} недоступен (попытка {}/{}), повтор через {} мс [applicationId={}]",
                        source, attempt, attempts, backoffMs, MDC.get("applicationId"));
                attempt++;
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    public void run(String source, Runnable call) {
        execute(source, () -> {
            call.run();
            return null;
        });
    }
}
