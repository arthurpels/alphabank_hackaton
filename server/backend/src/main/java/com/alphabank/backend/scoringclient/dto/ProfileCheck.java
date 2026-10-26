package com.alphabank.backend.scoringclient.dto;

import com.alphabank.backend.scoringclient.enums.ProfileStatus;

public record ProfileCheck(
        String okved,
        String activity,
        ProfileStatus status,
        String reason,
        String evidence,
        String violation,
        LlmMeta llm) {
}
