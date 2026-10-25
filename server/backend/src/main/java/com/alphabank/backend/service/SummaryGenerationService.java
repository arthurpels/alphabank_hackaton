package com.alphabank.backend.service;

import java.util.UUID;

import com.alphabank.backend.dto.response.UnderwritingSummaryResponseDto;

public interface SummaryGenerationService {

    UnderwritingSummaryResponseDto get(UUID applicationId);

    UnderwritingSummaryResponseDto refresh(UUID applicationId);
}
