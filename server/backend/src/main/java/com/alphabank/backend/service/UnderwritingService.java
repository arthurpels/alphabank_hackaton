package com.alphabank.backend.service;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.QueueItemDto;
import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.security.CurrentUser;

public interface UnderwritingService {

    List<QueueItemDto> getQueue(CurrentUser user, List<ApplicationStatus> statuses);

    UnderwritingSummaryDto getSummary(CurrentUser user, UUID applicationId);
}
