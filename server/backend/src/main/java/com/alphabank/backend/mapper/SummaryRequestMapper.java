package com.alphabank.backend.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.response.FactRefDto;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;
import com.alphabank.backend.scoringclient.dto.SummarySpot;

@Component
public class SummaryRequestMapper {

    private static final String DOCUMENT = "DOCUMENT";

    private final SourceFactsMapper sourceFacts;

    public SummaryRequestMapper(SourceFactsMapper sourceFacts) {
        this.sourceFacts = sourceFacts;
    }

    public SummaryRequest toRequest(ApplicationRecord application, ProductRecord product, ForkSnapshotRecord fork,
                                    List<SpotRecord> spots, Map<UUID, SpotAnswerRecord> answers,
                                    EnrichmentResult enrichment, List<RevenueSourceData> connected) {
        List<FactRefDto> facts = new ArrayList<>(registryFacts(enrichment, connected));
        spots.forEach(spot -> {
            SpotAnswerRecord answer = answers.get(spot.id());
            if (answer != null && answer.contractAmount() != null) {
                facts.add(new FactRefDto(DOCUMENT, "договор на " + sourceFacts.rubles(answer.contractAmount()) + " ₽, "
                        + answer.contractTermMonths() + " мес.", spot.id()));
            }
        });
        List<SummarySpot> summarySpots = spots.stream()
                .map(spot -> new SummarySpot(spot.id(), spot.type(), spot.title(), spot.status(), spot.pricePp(),
                        answers.containsKey(spot.id()) ? answers.get(spot.id()).text() : null))
                .toList();
        return new SummaryRequest(product.code(), application.amount(), application.termMonths(), application.goal(),
                fork.rateFrom(), fork.rateTo(), fork.approveMax() == null ? 0 : fork.approveMax(), summarySpots,
                facts);
    }

    private List<FactRefDto> registryFacts(EnrichmentResult enrichment, List<RevenueSourceData> connected) {
        List<FactRefDto> facts = new ArrayList<>();
        if (enrichment.bki() != null) {
            facts.add(new FactRefDto("BKI", sourceFacts.creditHistory(enrichment.bki()), null));
        }
        if (enrichment.egrul() != null && enrichment.egrul().registeredAt() != null) {
            facts.add(new FactRefDto("EGRUL", sourceFacts.registration(enrichment.egrul()), null));
        }
        if (enrichment.fns() != null && enrichment.fns().taxDebt() != null) {
            facts.add(new FactRefDto("FNS", sourceFacts.taxDebt(enrichment.fns()), null));
        }
        connected.forEach(source -> facts.add(new FactRefDto(source.sourceCode().name(), sourceFacts.revenue(source),
                null)));
        return facts;
    }
}
