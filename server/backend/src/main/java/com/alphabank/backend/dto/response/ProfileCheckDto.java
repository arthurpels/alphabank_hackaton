package com.alphabank.backend.dto.response;

import com.alphabank.backend.scoringclient.enums.ProfileStatus;

public record ProfileCheckDto(
        String okved,
        String activity,
        ProfileStatus status,
        String reason,
        String evidence,
        String violation,
        LlmMetaDto llm) {
}
