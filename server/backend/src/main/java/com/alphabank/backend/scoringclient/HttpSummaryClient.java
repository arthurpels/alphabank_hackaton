package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;

@Component
public class HttpSummaryClient implements SummaryClient {

    private final RestClient restClient;

    public HttpSummaryClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl) {
        this.restClient = builder.baseUrl(mlUrl).build();
    }

    @Override
    public UnderwritingSummaryDto summarize(SummaryRequest request) {
        return restClient.post().uri("/summary").body(request).retrieve().body(UnderwritingSummaryDto.class);
    }
}
