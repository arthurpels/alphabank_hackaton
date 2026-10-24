package com.alphabank.backend.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class HealthController {

    private static final long DEPENDENCY_TIMEOUT_MS = 1000;

    private final RestClient mlClient;
    private final RestClient mocksClient;

    public HealthController(@Value("${app.ml-url}") String mlUrl, @Value("${app.mocks-url}") String mocksUrl) {
        this.mlClient = dependencyClient(mlUrl);
        this.mocksClient = dependencyClient(mocksUrl);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("dependencies", Map.of(
                "ml", ping(mlClient),
                "mocks", ping(mocksClient)));
        return body;
    }

    private static RestClient dependencyClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) DEPENDENCY_TIMEOUT_MS);
        factory.setReadTimeout((int) DEPENDENCY_TIMEOUT_MS);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private static String ping(RestClient client) {
        try {
            Map<?, ?> response = client.get().uri("/health").retrieve().body(Map.class);
            return response != null && "ok".equals(response.get("status")) ? "up" : "down";
        } catch (Exception e) {
            return "down";
        }
    }
}
