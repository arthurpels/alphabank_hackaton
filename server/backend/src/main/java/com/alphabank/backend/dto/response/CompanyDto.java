package com.alphabank.backend.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import com.alphabank.backend.enums.LegalForm;

public record CompanyDto(
        UUID id,
        String inn,
        String name,
        LegalForm legalForm,
        String address,
        String directorFio,
        LocalDate registeredAt,
        String okvedMain) {
}
