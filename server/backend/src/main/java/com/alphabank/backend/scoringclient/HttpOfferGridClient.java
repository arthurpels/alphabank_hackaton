package com.alphabank.backend.scoringclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.scoringclient.dto.OfferGridResponse;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;

@Component
public class HttpOfferGridClient implements OfferGridClient {

    private final RestClient restClient;

    public HttpOfferGridClient(RestClient.Builder builder, @Value("${app.ml-url}") String mlUrl) {
        this.restClient = builder.baseUrl(mlUrl).build();
    }

    @Override
    public OfferGridResponse grid(ScoreRequest request) {
        return restClient.post().uri("/offers/grid").body(request).retrieve().body(OfferGridResponse.class);
    }
}
