package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;
import com.alphabank.backend.support.IoRetry;

@Component
public class HttpSummaryClient implements SummaryClient {

    private final RestClient restClient;
    private final IoRetry ioRetry;

    public HttpSummaryClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl, IoRetry ioRetry) {
        this.restClient = builder.baseUrl(mlUrl).build();
        this.ioRetry = ioRetry;
    }

    @Override
    public UnderwritingSummaryDto summarize(SummaryRequest request) {
        return ioRetry.execute("ml", () -> restClient.post()
                .uri("/summary")
                .body(request)
                .retrieve()
                .body(UnderwritingSummaryDto.class));
    }
}
