package com.alphabank.backend.service;

import java.util.UUID;

import com.alphabank.backend.dto.response.SourceChecksDto;
import com.alphabank.backend.security.CurrentUser;

public interface SourceCheckService {

    SourceChecksDto check(CurrentUser user, UUID applicationId);
}
