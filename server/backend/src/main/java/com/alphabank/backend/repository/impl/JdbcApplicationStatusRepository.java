package com.alphabank.backend.repository.impl;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.repository.ApplicationStatusRepository;

@Repository
public class JdbcApplicationStatusRepository implements ApplicationStatusRepository {

    private final JdbcClient jdbc;

    public JdbcApplicationStatusRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ApplicationStatus> findStatusForUpdate(UUID applicationId) {
        return jdbc.sql("SELECT status FROM application WHERE id = :id FOR UPDATE")
                .param("id", applicationId)
                .query(String.class)
                .optional()
                .map(ApplicationStatus::valueOf);
    }

    @Override
    public void updateStatus(UUID applicationId, ApplicationStatus status) {
        jdbc.sql("UPDATE application SET status = :status WHERE id = :id")
                .param("status", status.name())
                .param("id", applicationId)
                .update();
    }
}
