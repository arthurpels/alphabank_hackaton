package com.alphabank.backend.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

public record FieldDiffDto(String path, JsonNode expected, JsonNode actual) {
}
