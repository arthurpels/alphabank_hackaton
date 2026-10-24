package com.alphabank.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.repository.model.DocumentRecord;

public interface DocumentRepository {

    boolean existsForApplication(UUID documentId, UUID applicationId);

    void save(DocumentRecord document);

    Optional<DocumentRecord> findById(UUID id);
}
