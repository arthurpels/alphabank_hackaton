package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.support.IoRetry;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class HttpRulesClient implements RulesClient {

    private final RestClient restClient;
    private final IoRetry ioRetry;

    public HttpRulesClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl, IoRetry ioRetry) {
        this.restClient = builder.baseUrl(mlUrl).build();
        this.ioRetry = ioRetry;
    }

    @Override
    public JsonNode rules() {
        return ioRetry.execute("ml", () -> restClient.get().uri("/rules").retrieve().body(JsonNode.class));
    }
}
