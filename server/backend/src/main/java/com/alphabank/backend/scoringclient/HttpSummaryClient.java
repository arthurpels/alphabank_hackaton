package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;
import com.alphabank.backend.support.LlmCircuitBreaker;

@Component
public class HttpSummaryClient implements SummaryClient {

    private final RestClient restClient;
    private final LlmCircuitBreaker circuitBreaker;

    public HttpSummaryClient(RestClient.Builder builder,
                             @Qualifier("mlLlmRequestFactory") ClientHttpRequestFactory requestFactory,
                             @Value("${app.ml-url}") String mlUrl, LlmCircuitBreaker circuitBreaker) {
        this.restClient = builder.requestFactory(requestFactory).baseUrl(mlUrl).build();
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public UnderwritingSummaryDto summarize(SummaryRequest request) {
        return circuitBreaker.execute(() -> restClient.post()
                .uri("/summary")
                .body(request)
                .retrieve()
                .body(UnderwritingSummaryDto.class));
    }
}
