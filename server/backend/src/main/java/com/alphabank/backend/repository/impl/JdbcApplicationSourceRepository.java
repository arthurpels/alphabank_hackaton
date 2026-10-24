package com.alphabank.backend.repository.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.model.ApplicationSourceRecord;

@Repository
public class JdbcApplicationSourceRepository implements ApplicationSourceRepository {

    private final JdbcClient jdbc;

    public JdbcApplicationSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordConsent(UUID applicationId, SourceCode sourceCode, boolean consentGiven) {
        jdbc.sql("""
                INSERT INTO application_source (id, application_id, source_code, consent_given, connected_at)
                VALUES (gen_random_uuid(), :applicationId, :sourceCode, :consentGiven, now())
                ON CONFLICT (application_id, source_code) DO UPDATE SET
                    consent_given = EXCLUDED.consent_given,
                    connected_at = now()
                """)
                .param("applicationId", applicationId)
                .param("sourceCode", sourceCode.name())
                .param("consentGiven", consentGiven)
                .update();
    }

    @Override
    public Optional<ApplicationSourceRecord> find(UUID applicationId, SourceCode sourceCode) {
        return jdbc.sql("""
                SELECT id, application_id, source_code, consent_given, connected_at
                FROM application_source
                WHERE application_id = :applicationId AND source_code = :sourceCode
                """)
                .param("applicationId", applicationId)
                .param("sourceCode", sourceCode.name())
                .query((rs, rowNum) -> new ApplicationSourceRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("application_id", UUID.class),
                        SourceCode.valueOf(rs.getString("source_code")),
                        rs.getBoolean("consent_given"),
                        rs.getObject("connected_at", OffsetDateTime.class)))
                .optional();
    }

    @Override
    public List<ApplicationSourceRecord> findByApplicationId(UUID applicationId) {
        return jdbc.sql("""
                SELECT id, application_id, source_code, consent_given, connected_at
                FROM application_source
                WHERE application_id = :applicationId
                ORDER BY connected_at
                """)
                .param("applicationId", applicationId)
                .query((rs, rowNum) -> new ApplicationSourceRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("application_id", UUID.class),
                        SourceCode.valueOf(rs.getString("source_code")),
                        rs.getBoolean("consent_given"),
                        rs.getObject("connected_at", OffsetDateTime.class)))
                .list();
    }

    @Override
    public boolean isConsentGiven(UUID applicationId, SourceCode sourceCode) {
        return find(applicationId, sourceCode)
                .map(ApplicationSourceRecord::consentGiven)
                .orElse(false);
    }
}
