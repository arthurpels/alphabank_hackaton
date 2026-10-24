package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.scoringclient.dto.AnomaliesRequest;
import com.alphabank.backend.scoringclient.dto.AnomaliesResponse;
import com.alphabank.backend.support.IoRetry;

@Component
public class HttpAnomalyClient implements AnomalyClient {

    private final RestClient restClient;
    private final IoRetry ioRetry;

    public HttpAnomalyClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl, IoRetry ioRetry) {
        this.restClient = builder.baseUrl(mlUrl).build();
        this.ioRetry = ioRetry;
    }

    @Override
    public AnomaliesResponse detect(AnomaliesRequest request) {
        return ioRetry.execute("ml", () -> restClient.post()
                .uri("/anomalies")
                .body(request)
                .retrieve()
                .body(AnomaliesResponse.class));
    }
}
