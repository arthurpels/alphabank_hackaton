package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.scoringclient.dto.AnomaliesRequest;
import com.alphabank.backend.scoringclient.dto.AnomaliesResponse;

@Component
public class HttpAnomalyClient implements AnomalyClient {

    private final RestClient restClient;

    public HttpAnomalyClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl) {
        this.restClient = builder.baseUrl(mlUrl).build();
    }

    @Override
    public AnomaliesResponse detect(AnomaliesRequest request) {
        return restClient.post().uri("/anomalies").body(request).retrieve().body(AnomaliesResponse.class);
    }
}
