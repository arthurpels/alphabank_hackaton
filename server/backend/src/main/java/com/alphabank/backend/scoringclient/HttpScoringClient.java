package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;

@Component
public class HttpScoringClient implements ScoringClient {

    private final RestClient restClient;

    public HttpScoringClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl) {
        this.restClient = builder.baseUrl(mlUrl).build();
    }

    @Override
    public ScoreResponse score(ScoreRequest request) {
        return restClient.post()
                .uri("/score")
                .body(request)
                .retrieve()
                .body(ScoreResponse.class);
    }
}
