package com.alphabank.backend.service;

import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ProductRecord;

public interface ForkCalculationService {

    ForkCalculation calculate(ApplicationRecord application, ProductRecord product, String inn);
}
