package com.alphabank.backend.scoringclient.dto;

import com.alphabank.backend.enums.SourceCode;

public record AnomalyRef(String code, String month, int changePct, SourceCode source) {
}
