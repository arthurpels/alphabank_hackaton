package com.alphabank.backend.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.ContractDto;
import com.alphabank.backend.dto.response.PaymentScheduleItemDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.repository.ApplicationRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.service.ContractService;
import com.alphabank.backend.service.ExistingObligationsService;

@Service
public class ExistingObligationsServiceImpl implements ExistingObligationsService {

    private static final Set<ApplicationStatus> COMMITTED = Set.of(ApplicationStatus.SIGNING,
            ApplicationStatus.ISSUED);

    private final ApplicationRepository applicationRepository;
    private final ContractService contractService;

    public ExistingObligationsServiceImpl(ApplicationRepository applicationRepository,
                                          ContractService contractService) {
        this.applicationRepository = applicationRepository;
        this.contractService = contractService;
    }

    @Override
    public long bankMonthlyPayment(ApplicationRecord application, LocalDate asOf) {
        return applicationRepository.findByCompanyId(application.companyId()).stream()
                .filter(other -> !other.id().equals(application.id()))
                .filter(other -> COMMITTED.contains(other.status()))
                .map(contractService::find)
                .filter(Objects::nonNull)
                .filter(contract -> lastPaymentAfter(contract, asOf))
                .mapToLong(ContractDto::monthlyPayment)
                .sum();
    }

    private static boolean lastPaymentAfter(ContractDto contract, LocalDate asOf) {
        List<PaymentScheduleItemDto> schedule = contract.schedule();
        return schedule.get(schedule.size() - 1).date().isAfter(asOf);
    }
}
