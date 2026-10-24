package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;

@Component
public class HttpPurposeClient implements PurposeClient {

    private final RestClient restClient;

    public HttpPurposeClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl) {
        this.restClient = builder.baseUrl(mlUrl).build();
    }

    @Override
    public PurposeResponse classify(PurposeRequest request) {
        return restClient.post().uri("/purpose").body(request).retrieve().body(PurposeResponse.class);
    }
}
