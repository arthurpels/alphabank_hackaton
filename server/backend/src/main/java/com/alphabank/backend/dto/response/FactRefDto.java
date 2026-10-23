package com.alphabank.backend.dto.response;

import java.util.UUID;

public record FactRefDto(String source, String label, UUID spotId) {
}
