package com.alphabank.backend.enrichment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.enrichment.dto.AcquiringResponse;
import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.MarketplaceResponse;
import com.alphabank.backend.enrichment.dto.OfdResponse;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.enrichment.model.SpotDraft;
import com.alphabank.backend.enrichment.rule.SpotRules;
import com.alphabank.backend.enums.LegalForm;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.CompanyRepository;
import com.alphabank.backend.repository.model.CompanyRecord;
import com.alphabank.backend.scoringclient.dto.BkiCredit;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.enums.Collateral;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.Stability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EnrichmentService {

    private final MocksClient mocksClient;
    private final CompanyRepository companyRepository;
    private final ApplicationSourceRepository applicationSourceRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EnrichmentService(
            MocksClient mocksClient,
            CompanyRepository companyRepository,
            ApplicationSourceRepository applicationSourceRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.mocksClient = mocksClient;
        this.companyRepository = companyRepository;
        this.applicationSourceRepository = applicationSourceRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public EnrichmentResult enrich(String inn, boolean bkiConsent) {
        return enrich(inn, bkiConsent, LocalDate.now(clock));
    }

    public EnrichmentResult enrich(String inn, boolean bkiConsent, LocalDate asOf) {
        EgrulResponse egrul = mocksClient.egrul(inn);
        BkiResponse bki = bkiConsent ? mocksClient.bki(inn) : null;
        FnsResponse fns = mocksClient.fnsDebts(inn);
        List<RevenueSourceData> availableRevenue = probeRevenueSources(inn);
        List<SpotDraft> spots = SpotRules.evaluateInitialSpots(egrul, bki, fns, availableRevenue, asOf);

        return new EnrichmentResult(inn, egrul, bki, fns, availableRevenue, asOf, spots);
    }

    public List<RevenueSourceData> probeRevenueSources(String inn) {
        List<RevenueSourceData> list = new ArrayList<>();

        mocksClient.ofdRevenue(inn).ifPresent(ofd -> {
            long avg = calculateOfdAverageKopecks(ofd);
            list.add(new RevenueSourceData(SourceCode.OFD, avg, ofd));
        });

        mocksClient.marketplaceSales(inn).ifPresent(wb -> {
            long avg = calculateMarketplaceAverageKopecks(wb);
            list.add(new RevenueSourceData(SourceCode.MARKETPLACE, avg, wb));
        });

        mocksClient.acquiringTurnover(inn).ifPresent(acq -> {
            long avg = calculateAcquiringAverageKopecks(acq);
            list.add(new RevenueSourceData(SourceCode.ACQUIRING, avg, acq));
        });

        return List.copyOf(list);
    }

    public ScoreRequest createScoreRequest(
            EnrichmentResult enrichment,
            ProductCode productCode,
            long amountKopecks,
            int termMonths,
            Long confirmedMonthlyRevenueKopecks,
            Stability stability,
            Purpose purpose,
            Collateral collateral,
            long bankPayment) {

        EgrulResponse egrul = enrichment.egrul();
        BkiResponse bki = enrichment.bki();
        FnsResponse fns = enrichment.fns();

        LocalDate registeredAt = egrul != null ? egrul.registeredAt() : null;
        LocalDate directorSince = egrul != null && egrul.director() != null ? egrul.director().appointedAt() : null;
        boolean massAddress = egrul != null && egrul.address() != null && egrul.address().isMass();
        String egrulStatus = egrul != null ? egrul.status() : "UNKNOWN";

        Integer creditScore = bki != null ? bki.creditScore() : null;
        int delinq12m = bki != null && bki.delinquencies() != null ? bki.delinquencies().last12m() : 0;
        int bkiCurrentOverdue = bki != null && bki.delinquencies() != null ? bki.delinquencies().current() : 0;

        long taxDebtKopecks = fns != null && fns.taxDebt() != null ? fns.taxDebt().amount() * 100L : 0L;
        int unfiledReports = fns != null ? fns.unfiledReports() : 0;

        return new ScoreRequest(
                enrichment.asOf(),
                productCode,
                amountKopecks,
                termMonths,
                egrulStatus,
                registeredAt,
                directorSince,
                massAddress,
                creditScore,
                delinq12m,
                bkiCurrentOverdue,
                taxDebtKopecks,
                unfiledReports,
                confirmedMonthlyRevenueKopecks,
                stability,
                purpose,
                collateral,
                bankPayment,
                bkiCredits(bki));
    }

    private static List<BkiCredit> bkiCredits(BkiResponse bki) {
        if (bki == null || bki.openCredits() == null) {
            return List.of();
        }
        return bki.openCredits().stream()
                .filter(credit -> credit.debt() > 0)
                .map(credit -> new BkiCredit(credit.type(), credit.debt() * 100L))
                .toList();
    }

    public UUID persistCompanySnapshot(EgrulResponse egrul) {
        try {
            String json = objectMapper.writeValueAsString(egrul);
            LegalForm legalForm = LegalForm.valueOf(egrul.legalForm().toUpperCase());
            return companyRepository.upsertCompany(egrul.inn(), egrul.name(), legalForm, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка сериализации ЕГРЮЛ", e);
        }
    }

    public Optional<String> okvedMain(String inn) {
        return companyRepository.findByInn(inn)
                .map(CompanyRecord::egrulSnapshot)
                .filter(Objects::nonNull)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, EgrulResponse.class).okvedMain();
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Некорректный снимок ЕГРЮЛ в БД", e);
                    }
                });
    }

    public void recordConsent(UUID applicationId, SourceCode sourceCode, boolean consentGiven) {
        applicationSourceRepository.recordConsent(applicationId, sourceCode, consentGiven);
    }

    public Long calculateMonthlyRevenueKopecks(List<RevenueSourceData> connectedSources) {
        if (connectedSources == null || connectedSources.isEmpty()) {
            return null;
        }
        return connectedSources.stream()
                .mapToLong(RevenueSourceData::averageMonthlyKopecks)
                .max()
                .orElse(0L);
    }

    public static long calculateOfdAverageKopecks(OfdResponse ofd) {
        if (ofd == null || ofd.months() == null || ofd.months().isEmpty()) {
            return 0L;
        }
        long sum = ofd.months().stream().mapToLong(OfdResponse.MonthRevenue::revenue).sum();
        return Math.round((double) sum / ofd.months().size() * 100.0);
    }

    public static long calculateMarketplaceAverageKopecks(MarketplaceResponse wb) {
        if (wb == null || wb.months() == null || wb.months().isEmpty()) {
            return 0L;
        }
        long sum = wb.months().stream().mapToLong(MarketplaceResponse.MonthSales::payout).sum();
        return Math.round((double) sum / wb.months().size() * 100.0);
    }

    public static long calculateAcquiringAverageKopecks(AcquiringResponse acq) {
        if (acq == null || acq.months() == null || acq.months().isEmpty()) {
            return 0L;
        }
        long sum = acq.months().stream().mapToLong(AcquiringResponse.MonthTurnover::turnover).sum();
        return Math.round((double) sum / acq.months().size() * 100.0);
    }
}
