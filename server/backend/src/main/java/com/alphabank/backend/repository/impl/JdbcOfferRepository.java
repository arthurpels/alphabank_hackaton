package com.alphabank.backend.repository.impl;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.repository.OfferRepository;

@Repository
public class JdbcOfferRepository implements OfferRepository {

    private final JdbcClient jdbc;

    public JdbcOfferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int expireActive(UUID applicationId) {
        return jdbc.sql("""
                        UPDATE offer SET status = 'EXPIRED'
                        WHERE application_id = :applicationId AND status IN ('CALCULATED', 'OFFERED')
                        """)
                .param("applicationId", applicationId)
                .update();
    }
}
