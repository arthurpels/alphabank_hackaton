package com.alphabank.backend.model;

import com.alphabank.backend.dto.response.AnomalyVerdictDto;

public record ResolvedAnomalyAnswer(AnomalyVerdictDto verdict, boolean reused) {
}
