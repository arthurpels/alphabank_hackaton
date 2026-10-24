package com.alphabank.backend.scoringclient.dto;

import java.util.List;

public record AnomaliesResponse(List<Anomaly> anomalies) {
}
