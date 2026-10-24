package com.alphabank.backend.service;

import java.util.UUID;

import com.alphabank.backend.model.FactorStates;

public interface FactorStateService {

    FactorStates resolve(UUID applicationId, boolean revenueConfirmed, boolean contractOnly);
}
