package com.alphabank.backend.service;

import java.time.LocalDate;

import com.alphabank.backend.repository.model.ApplicationRecord;

public interface ExistingObligationsService {

    long bankMonthlyPayment(ApplicationRecord application, LocalDate asOf);
}
