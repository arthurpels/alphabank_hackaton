package com.alphabank.backend.scoringclient;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpMlVersionClient implements MlVersionClient {

    private static final String ENGINE_VERSION = "engineVersion";

    private final RestClient restClient;

    public HttpMlVersionClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl) {
        this.restClient = builder.baseUrl(mlUrl).build();
    }

    @Override
    public Optional<String> engineVersion() {
        try {
            Map<?, ?> health = restClient.get().uri("/health").retrieve().body(Map.class);
            return Optional.ofNullable(health).map(body -> body.get(ENGINE_VERSION)).map(Object::toString);
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }
}
