package com.alphabank.backend.service;

import java.util.List;

import com.alphabank.backend.dto.response.ProductDto;
import com.alphabank.backend.enums.ProductCode;

public interface CatalogService {

    List<ProductDto> getProducts();

    ProductDto getProduct(ProductCode code);
}
