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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

@Component
public class SummaryRequestMapper {

    private static final String DOCUMENT = "DOCUMENT";

    private final SourceFactsMapper sourceFacts;
    private final ObjectMapper objectMapper;

    public SummaryRequestMapper(SourceFactsMapper sourceFacts, ObjectMapper objectMapper) {
        this.sourceFacts = sourceFacts;
        this.objectMapper = objectMapper;
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
                facts, snapshotPayment(fork, true), snapshotPayment(fork, false));
    }

    private long snapshotPayment(ForkSnapshotRecord fork, boolean total) {
        if (fork.trace() == null) {
            return 0L;
        }
        return total
                ? json(fork.trace().scoreResponse()).path("policy").path("existingPayment").asLong(0L)
                : json(fork.trace().scoreRequest()).path("bankPayment").asLong(0L);
    }

    private JsonNode json(String text) {
        if (text == null) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректный снимок вилки в БД", e);
        }
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
