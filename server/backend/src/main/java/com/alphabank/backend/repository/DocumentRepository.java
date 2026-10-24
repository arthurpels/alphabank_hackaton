package com.alphabank.backend.repository;

import java.util.UUID;

public interface DocumentRepository {

    boolean existsForApplication(UUID documentId, UUID applicationId);
}
