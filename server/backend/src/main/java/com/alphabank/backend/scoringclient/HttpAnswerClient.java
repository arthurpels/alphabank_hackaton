package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.scoringclient.dto.AnswerClassifyRequest;
import com.alphabank.backend.scoringclient.dto.AnswerClassifyResponse;
import com.alphabank.backend.scoringclient.dto.AnswerVerifyRequest;
import com.alphabank.backend.scoringclient.dto.AnswerVerifyResponse;
import com.alphabank.backend.support.IoRetry;
import com.alphabank.backend.support.LlmCircuitBreaker;

@Component
public class HttpAnswerClient implements AnswerClient {

    private final RestClient llmClient;
    private final RestClient scoringClient;
    private final LlmCircuitBreaker circuitBreaker;
    private final IoRetry ioRetry;

    public HttpAnswerClient(RestClient.Builder builder,
                            @Qualifier("mlLlmRequestFactory") ClientHttpRequestFactory llmRequestFactory,
                            @Value("${app.ml-url}") String mlUrl, LlmCircuitBreaker circuitBreaker, IoRetry ioRetry) {
        this.llmClient = builder.clone().requestFactory(llmRequestFactory).baseUrl(mlUrl).build();
        this.scoringClient = builder.clone().baseUrl(mlUrl).build();
        this.circuitBreaker = circuitBreaker;
        this.ioRetry = ioRetry;
    }

    @Override
    public AnswerClassifyResponse classify(AnswerClassifyRequest request) {
        return circuitBreaker.execute(() -> llmClient.post()
                .uri("/answers/classify")
                .body(request)
                .retrieve()
                .body(AnswerClassifyResponse.class));
    }

    @Override
    public AnswerVerifyResponse verify(AnswerVerifyRequest request) {
        return ioRetry.execute("ml", () -> scoringClient.post()
                .uri("/answers/verify")
                .body(request)
                .retrieve()
                .body(AnswerVerifyResponse.class));
    }
}
