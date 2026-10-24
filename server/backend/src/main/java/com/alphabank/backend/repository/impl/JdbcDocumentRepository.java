package com.alphabank.backend.repository.impl;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.repository.DocumentRepository;

@Repository
public class JdbcDocumentRepository implements DocumentRepository {

    private final JdbcClient jdbc;

    public JdbcDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsForApplication(UUID documentId, UUID applicationId) {
        return jdbc.sql("SELECT count(*) FROM document WHERE id = :id AND application_id = :applicationId")
                .param("id", documentId)
                .param("applicationId", applicationId)
                .query(Integer.class)
                .single() > 0;
    }
}
