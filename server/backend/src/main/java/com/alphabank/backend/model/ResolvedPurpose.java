package com.alphabank.backend.model;

import com.alphabank.backend.dto.response.PurposeVerdictDto;

public record ResolvedPurpose(PurposeVerdictDto verdict, boolean reused) {
}
