package com.alphabank.backend.scoringclient.dto;

import java.util.List;

public record AnomaliesRequest(List<Series> series) {
}
