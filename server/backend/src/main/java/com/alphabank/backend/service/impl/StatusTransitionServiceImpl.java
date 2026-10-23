package com.alphabank.backend.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.repository.ApplicationStatusRepository;
import com.alphabank.backend.service.AuditService;
import com.alphabank.backend.service.StatusTransitionService;
import com.alphabank.backend.statemachine.ApplicationStateMachine;
import com.alphabank.backend.statemachine.ApplicationTransitions;

@Service
public class StatusTransitionServiceImpl implements StatusTransitionService {

    private final ApplicationStateMachine stateMachine;
    private final ApplicationStatusRepository statusRepository;
    private final AuditService auditService;

    public StatusTransitionServiceImpl(ApplicationStateMachine stateMachine,
                                       ApplicationStatusRepository statusRepository,
                                       AuditService auditService) {
        this.stateMachine = stateMachine;
        this.statusRepository = statusRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public ApplicationStatus start(UUID applicationId, UUID userId) {
        auditService.recordStatusChange(applicationId, null, ApplicationTransitions.INITIAL, userId, null);
        return ApplicationTransitions.INITIAL;
    }

    @Override
    @Transactional
    public ApplicationStatus apply(UUID applicationId, ApplicationStatus to, TransitionInitiator initiator,
                                   UUID userId, String reason) {
        ApplicationStatus from = statusRepository.findStatusForUpdate(applicationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Заявка " + applicationId + " не найдена"));
        stateMachine.transition(from, to, initiator);
        if (from != to) {
            statusRepository.updateStatus(applicationId, to);
            auditService.recordStatusChange(applicationId, from, to, userId, reason);
        }
        return to;
    }
}
