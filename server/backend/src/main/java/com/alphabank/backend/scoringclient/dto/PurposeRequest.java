package com.alphabank.backend.scoringclient.dto;

import com.alphabank.backend.enums.ProductCode;

public record PurposeRequest(ProductCode productCode, String goal, String okved) {
}
