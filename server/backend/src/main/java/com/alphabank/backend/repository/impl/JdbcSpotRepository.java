package com.alphabank.backend.repository.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.SpotRecord;

@Repository
public class JdbcSpotRepository implements SpotRepository {

    private final JdbcClient jdbc;

    public JdbcSpotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SpotRecord spot) {
        jdbc.sql("""
                        INSERT INTO spot (id, application_id, type, title, client_hint, scoring_factor, price_pp,
                                          close_method, status, added_by, source, removable, created_at, closed_at)
                        VALUES (:id, :applicationId, :type, :title, :clientHint, :scoringFactor, :pricePp,
                                :closeMethod, :status, :addedBy, :source, :removable, :createdAt, :closedAt)
                        """)
                .param("id", spot.id())
                .param("applicationId", spot.applicationId())
                .param("type", spot.type().name())
                .param("title", spot.title())
                .param("clientHint", spot.clientHint())
                .param("scoringFactor", spot.scoringFactor())
                .param("pricePp", spot.pricePp())
                .param("closeMethod", spot.closeMethod() == null ? null : spot.closeMethod().name())
                .param("status", spot.status().name())
                .param("addedBy", spot.addedBy().name())
                .param("source", spot.source() == null ? null : spot.source().name())
                .param("removable", spot.removable())
                .param("createdAt", spot.createdAt())
                .param("closedAt", spot.closedAt())
                .update();
    }

    @Override
    public List<SpotRecord> findByApplicationId(UUID applicationId) {
        return jdbc.sql("""
                        SELECT id, application_id, type, title, client_hint, scoring_factor, price_pp, close_method,
                               status, added_by, source, removable, created_at, closed_at
                        FROM spot
                        WHERE application_id = :applicationId
                        ORDER BY created_at, id
                        """)
                .param("applicationId", applicationId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    @Override
    public void updatePrice(UUID spotId, Double pricePp) {
        jdbc.sql("UPDATE spot SET price_pp = :pricePp WHERE id = :id")
                .param("pricePp", pricePp)
                .param("id", spotId)
                .update();
    }

    private static SpotRecord map(ResultSet rs) throws SQLException {
        String closeMethod = rs.getString("close_method");
        String source = rs.getString("source");
        double price = rs.getDouble("price_pp");
        boolean priceNull = rs.wasNull();
        boolean removable = rs.getBoolean("removable");
        boolean removableNull = rs.wasNull();
        return new SpotRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("application_id", UUID.class),
                SpotType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("client_hint"),
                rs.getString("scoring_factor"),
                priceNull ? null : price,
                closeMethod == null ? null : CloseMethod.valueOf(closeMethod),
                SpotStatus.valueOf(rs.getString("status")),
                DecidedBy.valueOf(rs.getString("added_by")),
                source == null ? null : SourceCode.valueOf(source),
                removableNull ? null : removable,
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("closed_at", OffsetDateTime.class));
    }
}
