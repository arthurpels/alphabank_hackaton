package com.alphabank.backend.repository.impl;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.repository.DocumentRepository;
import com.alphabank.backend.repository.model.DocumentRecord;

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

    @Override
    public void save(DocumentRecord document) {
        jdbc.sql("""
                        INSERT INTO document (id, application_id, kind, file_path, original_name, content_type)
                        VALUES (:id, :applicationId, :kind, :filePath, :originalName, :contentType)
                        """)
                .param("id", document.id())
                .param("applicationId", document.applicationId())
                .param("kind", document.kind())
                .param("filePath", document.filePath())
                .param("originalName", document.originalName())
                .param("contentType", document.contentType())
                .update();
    }

    @Override
    public Optional<DocumentRecord> findById(UUID id) {
        return jdbc.sql("SELECT * FROM document WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new DocumentRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("application_id", UUID.class),
                        rs.getString("kind"),
                        rs.getString("file_path"),
                        rs.getString("original_name"),
                        rs.getString("content_type"),
                        rs.getObject("uploaded_at", OffsetDateTime.class)))
                .optional();
    }
}
