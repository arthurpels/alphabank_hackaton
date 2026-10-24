package com.alphabank.backend.enrichment.model;

import java.time.LocalDate;
import java.util.List;

import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;

public record EnrichmentResult(
        String inn,
        EgrulResponse egrul,
        BkiResponse bki,
        FnsResponse fns,
        List<RevenueSourceData> availableRevenueSources,
        LocalDate asOf,
        List<SpotDraft> spots) {
}
