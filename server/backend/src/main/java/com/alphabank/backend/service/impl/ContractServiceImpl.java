package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.dto.response.ContractDto;
import com.alphabank.backend.dto.response.PaymentScheduleItemDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.OfferRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.OfferRecord;
import com.alphabank.backend.service.AuditService;
import com.alphabank.backend.service.ContractService;
import com.alphabank.backend.util.AnnuityScheduleCalculator;

@Service
public class ContractServiceImpl implements ContractService {

    private static final Set<ApplicationStatus> WITH_CONTRACT = Set.of(ApplicationStatus.SIGNING,
            ApplicationStatus.ISSUED);

    private final OfferRepository offerRepository;
    private final ForkSnapshotRepository forkSnapshotRepository;
    private final AuditService auditService;
    private final Clock clock;

    public ContractServiceImpl(OfferRepository offerRepository, ForkSnapshotRepository forkSnapshotRepository,
                               AuditService auditService, Clock clock) {
        this.offerRepository = offerRepository;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    public ContractDto find(ApplicationRecord application) {
        if (!WITH_CONTRACT.contains(application.status())) {
            return null;
        }
        OfferRecord offer = offerRepository.findByApplicationId(application.id()).stream()
                .filter(found -> found.status() == OfferStatus.CHOSEN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("У заявки в оформлении нет выбранного оффера"));
        ForkSnapshotRecord fork = forkSnapshotRepository.findById(offer.forkSnapshotId())
                .orElseThrow(() -> new IllegalStateException("Нет вилки оффера " + offer.id()));
        OffsetDateTime issuedAt = issuedAt(application);
        LocalDate start = (issuedAt != null ? issuedAt : offer.chosenAt()).atZoneSameInstant(clock.getZone())
                .toLocalDate();
        List<PaymentScheduleItemDto> schedule = AnnuityScheduleCalculator.calculate(offer.amount(), fork.rateTo(),
                offer.termMonths(), start.plusMonths(1));
        return new ContractDto(offer.id(), offer.amount(), offer.termMonths(), fork.rateTo(),
                schedule.get(0).payment(), issuedAt, issuedAt, schedule);
    }

    private OffsetDateTime issuedAt(ApplicationRecord application) {
        if (application.status() != ApplicationStatus.ISSUED) {
            return null;
        }
        return auditService.getHistory(application.id()).stream()
                .filter(event -> event.event() == AuditEventType.STATUS_CHANGE
                        && ApplicationStatus.ISSUED.name().equals(event.toState()))
                .map(AuditEventDto::createdAt)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("В журнале нет выдачи заявки " + application.id()));
    }
}
