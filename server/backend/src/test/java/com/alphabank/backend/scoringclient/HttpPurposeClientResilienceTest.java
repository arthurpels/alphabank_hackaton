package com.alphabank.backend.scoringclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.support.LlmCircuitBreaker;
import com.alphabank.backend.util.DemoClock;
import com.sun.net.httpserver.HttpServer;

class HttpPurposeClientResilienceTest {

    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void timeoutIsNotRetriedAndCircuitRecovers() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/purpose", exchange -> {
            int request = requests.incrementAndGet();
            if (request <= 2) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = """
                    {"status":"UNAVAILABLE","purpose":null,"applicable":true,"generatedBy":"KEYWORDS",
                     "matchedKeywords":[],"reason":null,"llm":null}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        DemoClock clock = new DemoClock(Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));
        HttpPurposeClient client = client(clock);
        PurposeRequest request = new PurposeRequest(ProductCode.EQUIPMENT, "оборудование");

        assertThrows(RestClientException.class, () -> client.classify(request));
        assertThrows(RestClientException.class, () -> client.classify(request));
        assertEquals(2, requests.get());

        assertThrows(RestClientException.class, () -> client.classify(request));
        assertEquals(2, requests.get());

        clock.advance(Duration.ofSeconds(31));
        client.classify(request);
        assertEquals(3, requests.get());
    }

    private HttpPurposeClient client(DemoClock clock) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(300);
        factory.setReadTimeout(200);
        LlmCircuitBreaker circuitBreaker = new LlmCircuitBreaker(2, 30_000, clock);
        return new HttpPurposeClient(RestClient.builder(), factory,
                "http://localhost:" + server.getAddress().getPort(), circuitBreaker);
    }
}
