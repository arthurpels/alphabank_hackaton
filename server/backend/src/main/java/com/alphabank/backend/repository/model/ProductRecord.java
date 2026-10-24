package com.alphabank.backend.repository.model;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.enums.ProductCode;

public record ProductRecord(
        UUID id,
        ProductCode code,
        String name,
        long minAmount,
        long maxAmount,
        List<Integer> allowedTerms) {
}
