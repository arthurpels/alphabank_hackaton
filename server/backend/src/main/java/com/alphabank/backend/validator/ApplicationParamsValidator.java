package com.alphabank.backend.validator;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.response.ProductDto;
import com.alphabank.backend.exception.ApiException;

@Component
public class ApplicationParamsValidator {

    public void validate(ProductDto product, Long amount, Integer termMonths) {
        if (amount != null && (amount < product.minAmount() || amount > product.maxAmount())) {
            throw ApiException.validation("amount вне диапазона продукта " + product.code() + ": от "
                    + product.minAmount() + " до " + product.maxAmount() + " коп.");
        }
        if (termMonths != null && !product.allowedTerms().contains(termMonths)) {
            throw ApiException.validation("termMonths для продукта " + product.code() + " — один из "
                    + product.allowedTerms());
        }
    }
}
