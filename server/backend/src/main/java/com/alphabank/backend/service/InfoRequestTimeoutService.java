package com.alphabank.backend.service;

import java.util.List;
import java.util.UUID;

public interface InfoRequestTimeoutService {

    List<UUID> cancelExpired();
}
