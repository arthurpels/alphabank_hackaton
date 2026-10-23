package com.alphabank.backend.service;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.request.AcceptRequest;
import com.alphabank.backend.dto.request.ApplicationCreateRequest;
import com.alphabank.backend.dto.request.ApplicationUpdateRequest;
import com.alphabank.backend.dto.request.CancelRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.ApplicationSummaryDto;
import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.dto.response.ForkDto;
import com.alphabank.backend.security.CurrentUser;

public interface ApplicationService {

    List<ApplicationSummaryDto> getApplications(CurrentUser user);

    ApplicationDetailsDto create(CurrentUser user, ApplicationCreateRequest request);

    ApplicationDetailsDto getApplication(CurrentUser user, UUID applicationId);

    ApplicationDetailsDto update(CurrentUser user, UUID applicationId, ApplicationUpdateRequest request);

    ApplicationDetailsDto evaluate(CurrentUser user, UUID applicationId);

    List<ForkDto> getForkHistory(CurrentUser user, UUID applicationId);

    ApplicationDetailsDto submit(CurrentUser user, UUID applicationId);

    ApplicationDetailsDto accept(CurrentUser user, UUID applicationId, AcceptRequest request);

    ApplicationDetailsDto sign(CurrentUser user, UUID applicationId);

    ApplicationDetailsDto cancel(CurrentUser user, UUID applicationId, CancelRequest request);

    List<AuditEventDto> getHistory(CurrentUser user, UUID applicationId);
}
