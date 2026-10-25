package com.alphabank.backend.scoringclient.dto;

public record LlmMeta(
        String model,
        String digest,
        String promptVersion,
        boolean cached) {
}
