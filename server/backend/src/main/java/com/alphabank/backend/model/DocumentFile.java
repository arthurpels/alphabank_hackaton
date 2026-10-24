package com.alphabank.backend.model;

import com.alphabank.backend.dto.response.DocumentDto;

public record DocumentFile(DocumentDto document, String contentType, byte[] content) {
}
