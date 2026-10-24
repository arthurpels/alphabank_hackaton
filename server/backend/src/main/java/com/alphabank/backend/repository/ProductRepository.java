package com.alphabank.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.repository.model.ProductRecord;

public interface ProductRepository {

    List<ProductRecord> findAll();

    Optional<ProductRecord> findByCode(ProductCode code);

    Optional<ProductRecord> findById(UUID id);
}
