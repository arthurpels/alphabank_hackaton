package com.alphabank.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alphabank.backend.config.ApiPaths;
import com.alphabank.backend.dto.response.ProductDto;
import com.alphabank.backend.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ApiPaths.BASE)
@Tag(name = "catalog", description = "справочники")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    @Operation(operationId = "listProducts", summary = "Кредитные продукты")
    public List<ProductDto> listProducts() {
        return catalogService.getProducts();
    }
}
