package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;
import com.alphabank.backend.support.LlmCircuitBreaker;

@Component
public class HttpPurposeClient implements PurposeClient {

    private final RestClient restClient;
    private final LlmCircuitBreaker circuitBreaker;

    public HttpPurposeClient(RestClient.Builder builder,
                             @Qualifier("mlLlmRequestFactory") ClientHttpRequestFactory requestFactory,
                             @Value("${app.ml-url}") String mlUrl, LlmCircuitBreaker circuitBreaker) {
        this.restClient = builder.requestFactory(requestFactory).baseUrl(mlUrl).build();
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public PurposeResponse classify(PurposeRequest request) {
        return circuitBreaker.execute(() -> restClient.post()
                .uri("/purpose")
                .body(request)
                .retrieve()
                .body(PurposeResponse.class));
    }
}
