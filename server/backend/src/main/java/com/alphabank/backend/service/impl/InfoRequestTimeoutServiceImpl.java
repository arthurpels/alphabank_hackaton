package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.repository.ApplicationRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.service.InfoRequestTimeoutService;
import com.alphabank.backend.service.StatusTransitionService;
import com.alphabank.backend.statemachine.ApplicationTransitions;

@Service
public class InfoRequestTimeoutServiceImpl implements InfoRequestTimeoutService {

    private static final String REASON = "Нет ответа на дозапрос " + ApplicationTransitions.INFO_REQUEST_DAYS
            + " дней";

    private final ApplicationRepository applicationRepository;
    private final StatusTransitionService statusTransitionService;
    private final Clock clock;

    public InfoRequestTimeoutServiceImpl(ApplicationRepository applicationRepository,
                                         StatusTransitionService statusTransitionService, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.statusTransitionService = statusTransitionService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<UUID> cancelExpired() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<UUID> expired = applicationRepository.findByStatuses(Set.of(ApplicationStatus.INFO_REQUESTED)).stream()
                .filter(application -> application.infoRequestedAt() != null)
                .filter(application -> !application.infoRequestedAt()
                        .plusDays(ApplicationTransitions.INFO_REQUEST_DAYS).isAfter(now))
                .map(ApplicationRecord::id)
                .toList();
        expired.forEach(id -> statusTransitionService.apply(id, ApplicationStatus.CANCELLED,
                TransitionInitiator.SYSTEM, null, REASON));
        return expired;
    }
}
