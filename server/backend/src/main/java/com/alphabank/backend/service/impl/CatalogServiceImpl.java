package com.alphabank.backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.ProductDto;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.mapper.ApplicationDetailsMapper;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.service.CatalogService;

@Service
public class CatalogServiceImpl implements CatalogService {

    private final ProductRepository productRepository;
    private final ApplicationDetailsMapper mapper;

    public CatalogServiceImpl(ProductRepository productRepository, ApplicationDetailsMapper mapper) {
        this.productRepository = productRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ProductDto> getProducts() {
        return productRepository.findAll().stream().map(mapper::toProduct).toList();
    }

    @Override
    public ProductDto getProduct(ProductCode code) {
        return productRepository.findByCode(code)
                .map(mapper::toProduct)
                .orElseThrow(() -> ApiException.validation("Неизвестный продукт " + code));
    }
}
