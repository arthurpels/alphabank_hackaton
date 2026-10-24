package com.alphabank.backend.repository.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.model.ProductRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcProductRepository implements ProductRepository {

    private static final String COLUMNS = """
            SELECT id, code, name, min_amount, max_amount, allowed_terms::text AS allowed_terms
            FROM product
            """;
    private static final TypeReference<List<Integer>> TERMS_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProductRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ProductRecord> findAll() {
        return jdbc.sql(COLUMNS + "ORDER BY code").query((rs, rowNum) -> map(rs)).list();
    }

    @Override
    public Optional<ProductRecord> findByCode(ProductCode code) {
        return jdbc.sql(COLUMNS + "WHERE code = :code")
                .param("code", code.name())
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    @Override
    public Optional<ProductRecord> findById(UUID id) {
        return jdbc.sql(COLUMNS + "WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    private ProductRecord map(ResultSet rs) throws SQLException {
        try {
            return new ProductRecord(
                    rs.getObject("id", UUID.class),
                    ProductCode.valueOf(rs.getString("code")),
                    rs.getString("name"),
                    rs.getLong("min_amount"),
                    rs.getLong("max_amount"),
                    objectMapper.readValue(rs.getString("allowed_terms"), TERMS_TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректные сроки продукта в БД", e);
        }
    }
}
