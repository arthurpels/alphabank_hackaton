package com.alphabank.backend.repository.model;

import java.util.UUID;

import com.alphabank.backend.enums.LegalForm;

public record CompanyRecord(
        UUID id,
        String inn,
        String name,
        LegalForm legalForm,
        String egrulSnapshot) {
}
