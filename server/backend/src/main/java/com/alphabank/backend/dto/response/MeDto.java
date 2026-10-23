package com.alphabank.backend.dto.response;

import java.util.UUID;

import com.alphabank.backend.enums.UserRole;

public record MeDto(
        UUID id,
        String fio,
        UserRole role,
        String personaTitle,
        CompanyDto company) {
}
