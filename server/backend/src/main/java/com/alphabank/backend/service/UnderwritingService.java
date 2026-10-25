package com.alphabank.backend.service;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.QueueItemDto;
import com.alphabank.backend.dto.response.UnderwritingSummaryResponseDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.security.CurrentUser;

public interface UnderwritingService {

    List<QueueItemDto> getQueue(CurrentUser user, List<ApplicationStatus> statuses);

    UnderwritingSummaryResponseDto getSummary(CurrentUser user, UUID applicationId);
}
