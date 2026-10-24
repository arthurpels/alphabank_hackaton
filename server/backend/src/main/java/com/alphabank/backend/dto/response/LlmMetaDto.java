package com.alphabank.backend.dto.response;

public record LlmMetaDto(
        String model,
        String digest,
        String promptVersion,
        boolean cached) {
}
