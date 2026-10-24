package com.alphabank.backend.scoringclient.dto;

import com.alphabank.backend.scoringclient.enums.StopFactorCode;

public record StopFactor(
        StopFactorCode code,
        boolean removable,
        String closeMethod) {
}
