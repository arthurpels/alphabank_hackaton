package com.alphabank.backend.dto.response;

import java.util.List;

import com.alphabank.backend.enums.ProductCode;

public record ProductDto(
        ProductCode code,
        String name,
        long minAmount,
        long maxAmount,
        List<Integer> allowedTerms) {
}
