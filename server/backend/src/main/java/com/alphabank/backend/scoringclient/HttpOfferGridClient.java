package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.scoringclient.dto.OfferGridResponse;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.support.IoRetry;

@Component
public class HttpOfferGridClient implements OfferGridClient {

    private final RestClient restClient;
    private final IoRetry ioRetry;

    public HttpOfferGridClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl, IoRetry ioRetry) {
        this.restClient = builder.baseUrl(mlUrl).build();
        this.ioRetry = ioRetry;
    }

    @Override
    public OfferGridResponse grid(ScoreRequest request) {
        return ioRetry.execute("ml", () -> restClient.post()
                .uri("/offers/grid")
                .body(request)
                .retrieve()
                .body(OfferGridResponse.class));
    }
}
