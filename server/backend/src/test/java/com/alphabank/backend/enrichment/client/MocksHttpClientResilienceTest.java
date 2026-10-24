package com.alphabank.backend.enrichment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import com.alphabank.backend.support.IoRetry;
import com.sun.net.httpserver.HttpServer;

class MocksHttpClientResilienceTest {

    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;

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
    void readTimeoutIsRetriedThenThrowsResourceAccessException() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        server = server(exchange -> {
            requests.incrementAndGet();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        MocksHttpClient client = clientWithShortTimeout(server);

        assertThrows(ResourceAccessException.class, () -> client.bki("7701234560"));
        assertEquals(3, requests.get());
    }

    @Test
    void connectRefusedIsRetriedThenThrowsResourceAccessException() throws IOException {
        server = server(exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        int deadPort = server.getAddress().getPort();
        server.stop(0);
        server = null;

        MocksHttpClient client = clientWithShortTimeout("http://localhost:" + deadPort);

        assertThrows(ResourceAccessException.class, () -> client.bki("7701234560"));
    }

    @Test
    void unprocessableEntityIsNotRetriedAndMapsToValidation() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        server = server(exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
        });

        MocksHttpClient client = clientWithShortTimeout(server);

        assertThrows(MockValidationException.class, () -> client.bki("7701234567"));
        assertEquals(1, requests.get());
    }

    @Test
    void notFoundIsNotRetriedAndMapsToNotFound() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        server = server(exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        MocksHttpClient client = clientWithShortTimeout(server);

        assertThrows(MockNotFoundException.class, () -> client.egrul("590111122233"));
        assertEquals(1, requests.get());
    }

    private static MocksHttpClient clientWithShortTimeout(HttpServer server) {
        return clientWithShortTimeout("http://localhost:" + server.getAddress().getPort());
    }

    private static MocksHttpClient clientWithShortTimeout(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(300);
        factory.setReadTimeout(300);
        return new MocksHttpClient(RestClient.builder().requestFactory(factory), baseUrl, new IoRetry(3, 10));
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/mock/bki/v1/report", handler);
        httpServer.createContext("/mock/egrul/v1/company", handler);
        executor = java.util.concurrent.Executors.newCachedThreadPool();
        httpServer.setExecutor(executor);
        httpServer.start();
        return httpServer;
    }
}
