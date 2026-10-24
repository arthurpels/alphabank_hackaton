package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.dto.request.AcceptRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.DecisionType;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.repository.DecisionRepository;
import com.alphabank.backend.repository.OfferRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.OfferRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.ApplicationDetailsService;
import com.alphabank.backend.service.AuditService;
import com.alphabank.backend.service.IssuanceService;
import com.alphabank.backend.service.StatusTransitionService;
import com.alphabank.backend.statemachine.ApplicationStateMachine;

@Service
public class IssuanceServiceImpl implements IssuanceService {

    private final ApplicationStateMachine stateMachine;
    private final ApplicationAccessService accessService;
    private final ApplicationDetailsService detailsService;
    private final OfferRepository offerRepository;
    private final DecisionRepository decisionRepository;
    private final AuditService auditService;
    private final StatusTransitionService statusTransitionService;
    private final Clock clock;

    public IssuanceServiceImpl(ApplicationStateMachine stateMachine, ApplicationAccessService accessService,
                               ApplicationDetailsService detailsService, OfferRepository offerRepository,
                               DecisionRepository decisionRepository, AuditService auditService,
                               StatusTransitionService statusTransitionService, Clock clock) {
        this.stateMachine = stateMachine;
        this.accessService = accessService;
        this.detailsService = detailsService;
        this.offerRepository = offerRepository;
        this.decisionRepository = decisionRepository;
        this.auditService = auditService;
        this.statusTransitionService = statusTransitionService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApplicationDetailsDto accept(CurrentUser user, UUID applicationId, AcceptRequest request) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.ACCEPT, user.role());
        OfferRecord offer = offerRepository.findByApplicationId(applicationId).stream()
                .filter(found -> found.id().equals(request.offerId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Оффер " + request.offerId() + " не найден в заявке"));
        if (offer.status() != OfferStatus.OFFERED) {
            throw ApiException.validation("Выбрать можно только предложенный вариант");
        }

        offerRepository.markChosen(offer.id(), OffsetDateTime.now(clock));
        offerRepository.declineOffered(applicationId);
        decisionRepository.findByApplicationId(applicationId)
                .filter(decision -> decision.type() == DecisionType.ALTERNATIVE)
                .ifPresent(decision -> decisionRepository.setOffer(applicationId, offer.id()));
        auditService.record(applicationId, AuditEventType.OFFER_CHOSEN, user.id(), "Клиент принял условия",
                Map.of("offerId", offer.id().toString()));
        statusTransitionService.apply(applicationId, ApplicationStatus.SIGNING, TransitionInitiator.CLIENT,
                user.id(), "Клиент принял условия");
        return detailsService.build(accessService.load(user, applicationId), user);
    }

    @Override
    @Transactional
    public ApplicationDetailsDto sign(CurrentUser user, UUID applicationId) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.SIGN, user.role());
        statusTransitionService.apply(applicationId, ApplicationStatus.ISSUED, TransitionInitiator.CLIENT,
                user.id(), "Договор подписан, деньги зачислены");
        return detailsService.build(accessService.load(user, applicationId), user);
    }
}
