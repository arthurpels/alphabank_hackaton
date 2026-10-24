package com.alphabank.backend.mapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.ApplicationDto;
import com.alphabank.backend.dto.response.ApplicationSummaryDto;
import com.alphabank.backend.dto.response.CompanyDto;
import com.alphabank.backend.dto.response.ConsentsDto;
import com.alphabank.backend.dto.response.ContractDto;
import com.alphabank.backend.dto.response.DecisionDto;
import com.alphabank.backend.dto.response.ForkDto;
import com.alphabank.backend.dto.response.OfferDto;
import com.alphabank.backend.dto.response.ProductDto;
import com.alphabank.backend.dto.response.SpotAnswerDto;
import com.alphabank.backend.dto.response.SpotDto;
import com.alphabank.backend.dto.response.ZoneThresholdsDto;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.CompanyRecord;
import com.alphabank.backend.repository.model.DecisionRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.OfferRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.statemachine.ApplicationTransitions;
import com.alphabank.backend.util.ForkZoneResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ApplicationDetailsMapper {

    private final ObjectMapper objectMapper;

    public ApplicationDetailsMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApplicationDetailsDto toDetails(ApplicationRecord application, CompanyRecord company, ProductRecord product,
                                           boolean bkiConsent, ForkSnapshotRecord fork, List<SpotRecord> spots,
                                           Map<UUID, SpotAnswerRecord> answers, List<OfferDto> offers,
                                           DecisionDto decision, ContractDto contract,
                                           List<ApplicationAction> actions) {
        ApplicationDto dto = new ApplicationDto(application.id(), application.status(), toCompany(company),
                toProduct(product), application.amount(), application.termMonths(), application.goal(),
                new ConsentsDto(bkiConsent, true), actions, application.createdAt(), application.submittedAt(),
                application.infoRequestedAt(),
                application.status() == ApplicationStatus.INFO_REQUESTED && application.infoRequestedAt() != null
                        ? application.infoRequestedAt().plusDays(ApplicationTransitions.INFO_REQUEST_DAYS) : null);
        return new ApplicationDetailsDto(dto, fork == null ? null : toFork(fork),
                spots.stream().map(spot -> toSpot(spot, answers.get(spot.id()))).toList(), offers, decision,
                contract);
    }

    public ApplicationSummaryDto toSummary(ApplicationRecord application, ProductRecord product,
                                           ForkSnapshotRecord fork) {
        return new ApplicationSummaryDto(application.id(), application.status(), product.code(),
                application.amount(), application.termMonths(), fork == null ? null : toFork(fork),
                application.createdAt());
    }

    public ForkDto toFork(ForkSnapshotRecord fork) {
        ZoneThresholdsDto thresholds = new ZoneThresholdsDto(
                fork.approveMax() == null ? 0 : fork.approveMax(),
                fork.rejectMin() == null ? 0 : fork.rejectMin());
        return new ForkDto(fork.id(), fork.rateFrom(), fork.rateTo(),
                ForkZoneResolver.resolve(fork.rateFrom(), fork.rateTo(), thresholds), thresholds,
                fork.rulesVersion(), fork.asOf(), fork.calculatedAt());
    }

    public OfferDto toOffer(OfferRecord offer, ProductCode productCode, ForkSnapshotRecord fork, boolean requested) {
        return new OfferDto(offer.id(), productCode, offer.amount(), offer.termMonths(), offer.collateralType(),
                offer.collateralComment(), toFork(fork), offer.proposedBy(), offer.status(), requested,
                offer.proposedBy() == DecidedBy.UNDERWRITER, offer.comment(), offer.chosenAt());
    }

    public DecisionDto toDecision(DecisionRecord decision) {
        return new DecisionDto(decision.id(), decision.type(), decision.decidedBy(), decision.decidedByUserId(),
                decision.rulesVersion(),
                decision.override(), decision.comment(), decision.reasonSpotId(), decision.decidedAt());
    }

    public ProductDto toProduct(ProductRecord product) {
        return new ProductDto(product.code(), product.name(), product.minAmount(), product.maxAmount(),
                product.allowedTerms());
    }

    public SpotDto toSpot(SpotRecord spot, SpotAnswerRecord answer) {
        return new SpotDto(spot.id(), spot.type(), spot.title(), spot.clientHint(), spot.scoringFactor(),
                spot.pricePp() == null ? 0 : spot.pricePp(), spot.closeMethod(), spot.status(), spot.addedBy(),
                spot.source(), spot.removable(), spot.answerOptions(), answer == null ? null : toAnswer(answer),
                spot.createdAt(), spot.closedAt());
    }

    private static SpotAnswerDto toAnswer(SpotAnswerRecord answer) {
        return new SpotAnswerDto(answer.id(), answer.optionCode(), answer.text(), answer.documentId(),
                answer.contractAmount(), answer.contractTermMonths(), answer.verdict(), answer.reviewedBy(),
                answer.reviewComment(), answer.createdAt(), answer.reviewedAt(), answer.purposeVerdict());
    }

    private CompanyDto toCompany(CompanyRecord company) {
        EgrulResponse egrul = parseEgrul(company.egrulSnapshot());
        return new CompanyDto(company.id(), company.inn(), company.name(), company.legalForm(),
                egrul == null || egrul.address() == null ? null : egrul.address().value(),
                egrul == null || egrul.director() == null ? null : egrul.director().fio(),
                egrul == null ? null : egrul.registeredAt(),
                egrul == null ? null : egrul.okvedMain());
    }

    private EgrulResponse parseEgrul(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, EgrulResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректный снимок ЕГРЮЛ в БД", e);
        }
    }
}
