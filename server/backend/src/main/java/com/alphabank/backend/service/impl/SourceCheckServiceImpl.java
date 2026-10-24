package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.SourceCheckDto;
import com.alphabank.backend.dto.response.SourceChecksDto;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.enrichment.model.SpotDraft;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.CheckStatus;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.mapper.SourceFactsMapper;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.SourceCheckService;

@Service
public class SourceCheckServiceImpl implements SourceCheckService {

    private final ApplicationAccessService accessService;
    private final ApplicationSourceRepository applicationSourceRepository;
    private final EnrichmentService enrichmentService;
    private final SourceFactsMapper facts;
    private final Clock clock;

    public SourceCheckServiceImpl(ApplicationAccessService accessService,
                                  ApplicationSourceRepository applicationSourceRepository,
                                  EnrichmentService enrichmentService, SourceFactsMapper facts, Clock clock) {
        this.accessService = accessService;
        this.applicationSourceRepository = applicationSourceRepository;
        this.enrichmentService = enrichmentService;
        this.facts = facts;
        this.clock = clock;
    }

    @Override
    public SourceChecksDto check(CurrentUser user, UUID applicationId) {
        ApplicationRecord application = accessService.load(user, applicationId);
        if (application.status() == ApplicationStatus.DRAFT) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION, "Проверка источников доступна после расчёта",
                    application.status());
        }
        boolean bkiConsent = applicationSourceRepository.isConsentGiven(applicationId, SourceCode.BKI);
        EnrichmentResult enrichment = enrichmentService.enrich(accessService.companyInn(application), bkiConsent,
                LocalDate.now(clock));

        List<SourceCheckDto> items = new ArrayList<>();
        items.add(item(enrichment, SourceCode.EGRUL, egrulFacts(enrichment.egrul())));
        if (enrichment.fns() != null) {
            items.add(item(enrichment, SourceCode.FNS, List.of(facts.taxDebt(enrichment.fns()),
                    facts.unfiledReports(enrichment.fns()))));
        }
        if (enrichment.bki() != null) {
            items.add(item(enrichment, SourceCode.BKI, List.of(facts.creditHistory(enrichment.bki()),
                    facts.currentOverdue(enrichment.bki()))));
        }
        return new SourceChecksDto(OffsetDateTime.now(clock), items);
    }

    private List<String> egrulFacts(EgrulResponse egrul) {
        List<String> found = new ArrayList<>(List.of(facts.company(egrul), facts.status(egrul)));
        if (egrul.registeredAt() != null) {
            found.add(facts.registration(egrul));
        }
        if (egrul.director() != null && egrul.director().fio() != null) {
            found.add(facts.director(egrul));
        }
        if (egrul.address() != null && egrul.address().value() != null) {
            found.add(facts.address(egrul));
        }
        return found;
    }

    private static SourceCheckDto item(EnrichmentResult enrichment, SourceCode source, List<String> found) {
        List<SpotDraft> stops = enrichment.spots().stream()
                .filter(draft -> draft.type() == SpotType.STOP_FACTOR && draft.source() == source)
                .toList();
        CheckStatus status = stops.stream().anyMatch(draft -> !Boolean.TRUE.equals(draft.removable()))
                ? CheckStatus.BLOCKED
                : stops.isEmpty() ? CheckStatus.OK : CheckStatus.WARNING;
        return new SourceCheckDto(source, status, found, stops.stream().map(SpotDraft::title).toList());
    }
}
