package com.alphabank.backend.repository.impl;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.dto.response.CompanyDto;
import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.repository.AppUserRepository;

@Repository
public class JdbcAppUserRepository implements AppUserRepository {

    private final JdbcClient jdbc;

    public JdbcAppUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ensureExists(MeDto user) {
        CompanyDto company = user.company();
        if (company != null) {
            jdbc.sql("""
                            INSERT INTO company (id, inn, name, legal_form)
                            VALUES (:id, :inn, :name, :legalForm)
                            ON CONFLICT (inn) DO NOTHING
                            """)
                    .param("id", company.id())
                    .param("inn", company.inn())
                    .param("name", company.name())
                    .param("legalForm", company.legalForm().name())
                    .update();
        }
        jdbc.sql("""
                        INSERT INTO app_user (id, company_id, role, fio)
                        VALUES (:id, (SELECT id FROM company WHERE inn = :inn), :role, :fio)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", user.id())
                .param("inn", company == null ? null : company.inn())
                .param("role", user.role().name())
                .param("fio", user.fio())
                .update();
    }
}
