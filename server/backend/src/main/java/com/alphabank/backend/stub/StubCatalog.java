package com.alphabank.backend.stub;

import java.util.List;

import com.alphabank.backend.dto.response.ProductDto;
import com.alphabank.backend.enums.ProductCode;

public final class StubCatalog {

    private StubCatalog() {
    }

    public static List<ProductDto> products() {
        return List.of(
                new ProductDto(ProductCode.WORKING_CAPITAL, "Оборотный кредит", 30_000_000L, 1_000_000_000L,
                        List.of(6, 12, 24)),
                new ProductDto(ProductCode.EQUIPMENT, "Кредит на оборудование", 50_000_000L, 1_500_000_000L,
                        List.of(12, 24, 36)),
                new ProductDto(ProductCode.OVERDRAFT, "Овердрафт", 10_000_000L, 300_000_000L, List.of(12)));
    }

    public static ProductDto product(ProductCode code) {
        return products().stream().filter(p -> p.code() == code).findFirst().orElseThrow();
    }
}
