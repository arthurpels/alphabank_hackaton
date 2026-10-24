package com.alphabank.backend.enrichment.dto;

import com.alphabank.backend.enums.SourceCode;

public record RevenueSourceData(
        SourceCode sourceCode,
        long averageMonthlyKopecks,
        Object rawResponse) {
}
