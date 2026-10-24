package com.alphabank.backend.repository.impl;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.enums.LegalForm;
import com.alphabank.backend.repository.CompanyRepository;
import com.alphabank.backend.repository.model.CompanyRecord;

@Repository
public class JdbcCompanyRepository implements CompanyRepository {

    private final JdbcClient jdbc;

    public JdbcCompanyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID upsertCompany(String inn, String name, LegalForm legalForm, String egrulSnapshotJson) {
        return jdbc.sql("""
                INSERT INTO company (id, inn, name, legal_form, egrul_snapshot)
                VALUES (gen_random_uuid(), :inn, :name, :legalForm, :egrulSnapshot::jsonb)
                ON CONFLICT (inn) DO UPDATE SET
                    name = EXCLUDED.name,
                    legal_form = EXCLUDED.legal_form,
                    egrul_snapshot = EXCLUDED.egrul_snapshot
                RETURNING id
                """)
                .param("inn", inn)
                .param("name", name)
                .param("legalForm", legalForm.name())
                .param("egrulSnapshot", egrulSnapshotJson)
                .query(UUID.class)
                .single();
    }

    @Override
    public Optional<CompanyRecord> findByInn(String inn) {
        return jdbc.sql("""
                SELECT id, inn, name, legal_form, egrul_snapshot::text AS egrul_snapshot
                FROM company
                WHERE inn = :inn
                """)
                .param("inn", inn)
                .query((rs, rowNum) -> new CompanyRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("inn"),
                        rs.getString("name"),
                        LegalForm.valueOf(rs.getString("legal_form")),
                        rs.getString("egrul_snapshot")))
                .optional();
    }

    @Override
    public Optional<CompanyRecord> findById(UUID id) {
        return jdbc.sql("""
                SELECT id, inn, name, legal_form, egrul_snapshot::text AS egrul_snapshot
                FROM company
                WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new CompanyRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("inn"),
                        rs.getString("name"),
                        LegalForm.valueOf(rs.getString("legal_form")),
                        rs.getString("egrul_snapshot")))
                .optional();
    }

    @Override
    public void updateEgrulSnapshot(String inn, String egrulSnapshotJson) {
        jdbc.sql("""
                UPDATE company
                SET egrul_snapshot = :egrulSnapshot::jsonb
                WHERE inn = :inn
                """)
                .param("egrulSnapshot", egrulSnapshotJson)
                .param("inn", inn)
                .update();
    }
}
