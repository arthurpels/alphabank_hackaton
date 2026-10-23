package com.alphabank.backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.ProductDto;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.service.CatalogService;
import com.alphabank.backend.stub.StubCatalog;

@Service
public class CatalogServiceImpl implements CatalogService {

    @Override
    public List<ProductDto> getProducts() {
        return StubCatalog.products();
    }

    @Override
    public ProductDto getProduct(ProductCode code) {
        return StubCatalog.product(code);
    }
}
