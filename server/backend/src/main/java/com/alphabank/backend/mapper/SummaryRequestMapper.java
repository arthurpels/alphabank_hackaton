package com.alphabank.backend.mapper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.response.FactRefDto;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.enrichment.rule.SpotRules;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;
import com.alphabank.backend.scoringclient.dto.SummarySpot;

@Component
public class SummaryRequestMapper {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String IP = "IP";
    private static final String DOCUMENT = "DOCUMENT";

    public SummaryRequest toRequest(ApplicationRecord application, ProductRecord product, ForkSnapshotRecord fork,
                                    List<SpotRecord> spots, Map<UUID, SpotAnswerRecord> answers,
                                    EnrichmentResult enrichment, List<RevenueSourceData> connected) {
        List<FactRefDto> facts = new ArrayList<>(sourceFacts(enrichment, connected));
        spots.forEach(spot -> {
            SpotAnswerRecord answer = answers.get(spot.id());
            if (answer != null && answer.contractAmount() != null) {
                facts.add(new FactRefDto(DOCUMENT, "договор на " + rubles(answer.contractAmount()) + " ₽, "
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

    private static List<FactRefDto> sourceFacts(EnrichmentResult enrichment, List<RevenueSourceData> connected) {
        List<FactRefDto> facts = new ArrayList<>();
        if (enrichment.bki() != null) {
            int delinquencies = enrichment.bki().delinquencies() == null ? 0
                    : enrichment.bki().delinquencies().last12m();
            facts.add(new FactRefDto("BKI", "кредитный рейтинг " + enrichment.bki().creditScore()
                    + (delinquencies == 0 ? ", просрочек за 12 мес. нет" : ", просрочек за 12 мес.: " + delinquencies),
                    null));
        }
        if (enrichment.egrul() != null && enrichment.egrul().registeredAt() != null) {
            facts.add(new FactRefDto("EGRUL", (IP.equalsIgnoreCase(enrichment.egrul().legalForm())
                    ? "ИП зарегистрирован " : "компания зарегистрирована ")
                    + enrichment.egrul().registeredAt().format(DATE), null));
        }
        if (enrichment.fns() != null && enrichment.fns().taxDebt() != null) {
            long debt = enrichment.fns().taxDebt().amount();
            facts.add(new FactRefDto("FNS", debt == 0 ? "долга перед ФНС нет"
                    : "долг перед ФНС " + SpotRules.formatRubles(debt) + " ₽", null));
        }
        connected.forEach(source -> facts.add(new FactRefDto(source.sourceCode().name(),
                "выручка в среднем " + rubles(source.averageMonthlyKopecks()) + " ₽ в месяц", null)));
        return facts;
    }

    private static String rubles(long kopecks) {
        return SpotRules.formatRubles(kopecks / 100);
    }
}
