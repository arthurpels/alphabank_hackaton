package com.alphabank.backend.scoringclient.dto;

import java.util.List;

public record OfferGridResponse(String rulesVersion, List<Offer> offers) {
}
