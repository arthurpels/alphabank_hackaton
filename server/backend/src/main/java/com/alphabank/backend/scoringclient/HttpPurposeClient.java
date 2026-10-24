package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;
import com.alphabank.backend.support.IoRetry;

@Component
public class HttpPurposeClient implements PurposeClient {

    private final RestClient restClient;
    private final IoRetry ioRetry;

    public HttpPurposeClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl, IoRetry ioRetry) {
        this.restClient = builder.baseUrl(mlUrl).build();
        this.ioRetry = ioRetry;
    }

    @Override
    public PurposeResponse classify(PurposeRequest request) {
        return ioRetry.execute("ml", () -> restClient.post()
                .uri("/purpose")
                .body(request)
                .retrieve()
                .body(PurposeResponse.class));
    }
}
