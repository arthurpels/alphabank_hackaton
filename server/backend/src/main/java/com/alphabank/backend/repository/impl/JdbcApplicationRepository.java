package com.alphabank.backend.repository.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.repository.ApplicationRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;

@Repository
public class JdbcApplicationRepository implements ApplicationRepository {

    private static final String COLUMNS = """
            SELECT id, company_id, user_id, product_id, amount, term_months, goal, status,
                   created_at, submitted_at, info_requested_at
            FROM application
            """;

    private final JdbcClient jdbc;

    public JdbcApplicationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ApplicationRecord application) {
        jdbc.sql("""
                        INSERT INTO application (id, company_id, user_id, product_id, amount, term_months, goal,
                                                 status, created_at)
                        VALUES (:id, :companyId, :userId, :productId, :amount, :termMonths, :goal,
                                :status, :createdAt)
                        """)
                .param("id", application.id())
                .param("companyId", application.companyId())
                .param("userId", application.userId())
                .param("productId", application.productId())
                .param("amount", application.amount())
                .param("termMonths", application.termMonths())
                .param("goal", application.goal())
                .param("status", application.status().name())
                .param("createdAt", application.createdAt())
                .update();
    }

    @Override
    public Optional<ApplicationRecord> findById(UUID id) {
        return jdbc.sql(COLUMNS + "WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    @Override
    public List<ApplicationRecord> findByCompanyId(UUID companyId) {
        return jdbc.sql(COLUMNS + "WHERE company_id = :companyId ORDER BY created_at DESC")
                .param("companyId", companyId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    @Override
    public void updateParameters(UUID id, UUID productId, long amount, int termMonths, String goal) {
        jdbc.sql("""
                        UPDATE application
                        SET product_id = :productId, amount = :amount, term_months = :termMonths, goal = :goal
                        WHERE id = :id
                        """)
                .param("productId", productId)
                .param("amount", amount)
                .param("termMonths", termMonths)
                .param("goal", goal)
                .param("id", id)
                .update();
    }

    private static ApplicationRecord map(ResultSet rs) throws SQLException {
        return new ApplicationRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("company_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getLong("amount"),
                rs.getInt("term_months"),
                rs.getString("goal"),
                ApplicationStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getObject("info_requested_at", OffsetDateTime.class));
    }
}
