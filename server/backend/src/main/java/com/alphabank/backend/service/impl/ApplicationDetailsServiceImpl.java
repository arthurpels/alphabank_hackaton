package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.DecisionDto;
import com.alphabank.backend.dto.response.OfferDto;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.mapper.ApplicationDetailsMapper;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.CompanyRepository;
import com.alphabank.backend.repository.DecisionRepository;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.OfferRepository;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.CompanyRecord;
import com.alphabank.backend.repository.model.OfferRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationDetailsService;
import com.alphabank.backend.statemachine.ApplicationStateMachine;

@Service
public class ApplicationDetailsServiceImpl implements ApplicationDetailsService {

    private static final Set<OfferStatus> CLIENT_OFFERS = Set.of(OfferStatus.OFFERED, OfferStatus.CHOSEN);
    private static final Set<OfferStatus> UNDERWRITER_OFFERS = Set.of(OfferStatus.CALCULATED, OfferStatus.OFFERED,
            OfferStatus.CHOSEN);

    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final ApplicationSourceRepository applicationSourceRepository;
    private final ForkSnapshotRepository forkSnapshotRepository;
    private final SpotRepository spotRepository;
    private final SpotAnswerRepository spotAnswerRepository;
    private final OfferRepository offerRepository;
    private final DecisionRepository decisionRepository;
    private final ApplicationStateMachine stateMachine;
    private final ApplicationDetailsMapper mapper;

    public ApplicationDetailsServiceImpl(CompanyRepository companyRepository, ProductRepository productRepository,
                                         ApplicationSourceRepository applicationSourceRepository,
                                         ForkSnapshotRepository forkSnapshotRepository, SpotRepository spotRepository,
                                         SpotAnswerRepository spotAnswerRepository, OfferRepository offerRepository,
                                         DecisionRepository decisionRepository, ApplicationStateMachine stateMachine,
                                         ApplicationDetailsMapper mapper) {
        this.companyRepository = companyRepository;
        this.productRepository = productRepository;
        this.applicationSourceRepository = applicationSourceRepository;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.spotRepository = spotRepository;
        this.spotAnswerRepository = spotAnswerRepository;
        this.offerRepository = offerRepository;
        this.decisionRepository = decisionRepository;
        this.stateMachine = stateMachine;
        this.mapper = mapper;
    }

    @Override
    public ApplicationDetailsDto build(ApplicationRecord application, CurrentUser user) {
        CompanyRecord company = companyRepository.findById(application.companyId())
                .orElseThrow(() -> new IllegalStateException("Компания заявки не найдена"));
        List<SpotRecord> spots = spotRepository.findByApplicationId(application.id());
        DecisionDto decision = decisionRepository.findByApplicationId(application.id())
                .map(mapper::toDecision)
                .orElse(null);
        return mapper.toDetails(application, company, product(application.productId()),
                applicationSourceRepository.isConsentGiven(application.id(), SourceCode.BKI),
                forkSnapshotRepository.findLatest(application.id()).orElse(null), spots,
                spotAnswerRepository.findLatestBySpotIds(spots.stream().map(SpotRecord::id).toList()),
                offers(application, user), decision,
                stateMachine.availableActions(application.status(), user.role()));
    }

    @Override
    public List<OfferDto> offers(ApplicationRecord application, CurrentUser user) {
        Set<OfferStatus> visible = user.isUnderwriter() ? UNDERWRITER_OFFERS : CLIENT_OFFERS;
        return offerRepository.findByApplicationId(application.id()).stream()
                .filter(offer -> visible.contains(offer.status()))
                .map(offer -> toOffer(application, offer))
                .toList();
    }

    private OfferDto toOffer(ApplicationRecord application, OfferRecord offer) {
        ProductRecord product = product(offer.productId());
        boolean requested = offer.productId().equals(application.productId())
                && offer.amount() == application.amount() && offer.termMonths() == application.termMonths()
                && offer.collateralType() == null;
        return mapper.toOffer(offer, product.code(),
                forkSnapshotRepository.findById(offer.forkSnapshotId()).orElseThrow(), requested);
    }

    private ProductRecord product(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Продукт не найден"));
    }
}
