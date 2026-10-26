package com.alphabank.backend.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.enrichment.dto.AcquiringResponse;
import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.MarketplaceResponse;
import com.alphabank.backend.enrichment.dto.OfdResponse;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.LegalForm;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.CompanyRepository;
import com.alphabank.backend.scoringclient.dto.BkiCredit;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.enums.Collateral;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.Stability;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class EnrichmentServiceTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 10, 23);

    private MocksClient mocksClient;
    private CompanyRepository companyRepository;
    private ApplicationSourceRepository applicationSourceRepository;
    private ObjectMapper objectMapper;
    private Clock clock;
    private EnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        mocksClient = mock(MocksClient.class);
        companyRepository = mock(CompanyRepository.class);
        applicationSourceRepository = mock(ApplicationSourceRepository.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);

        enrichmentService = new EnrichmentService(
                mocksClient,
                companyRepository,
                applicationSourceRepository,
                objectMapper,
                clock);
    }

    @Test
    void enrichFetchesSourcesAndBuildsInitialSpots() {
        String inn = "7701234560";

        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", inn, "ООО «Кофейня»", "OOO", "ACTIVE",
                LocalDate.of(2019, 4, 15),
                new EgrulResponse.Director("Смирнова А. В.", LocalDate.of(2019, 4, 15), false),
                new EgrulResponse.Address("г. Москва, ул. Покровка, 12", false),
                "56.30", 2);

        BkiResponse bki = new BkiResponse(
                "BKI", inn, 712,
                List.of(), 3,
                new BkiResponse.Delinquencies(0, 5, 0), 1);

        FnsResponse fns = new FnsResponse(
                "FNS", inn,
                new FnsResponse.TaxDebt(0, "RUB", LocalDate.of(2026, 10, 15), null),
                0, "USN");

        OfdResponse ofd = new OfdResponse(
                "OFD", inn,
                List.of(
                        new OfdResponse.MonthRevenue("2025-10", 1410000),
                        new OfdResponse.MonthRevenue("2025-11", 1380000),
                        new OfdResponse.MonthRevenue("2025-12", 1560000),
                        new OfdResponse.MonthRevenue("2026-01", 1450000),
                        new OfdResponse.MonthRevenue("2026-02", 1520000),
                        new OfdResponse.MonthRevenue("2026-03", 830000),
                        new OfdResponse.MonthRevenue("2026-04", 1490000),
                        new OfdResponse.MonthRevenue("2026-05", 1470000),
                        new OfdResponse.MonthRevenue("2026-06", 1430000),
                        new OfdResponse.MonthRevenue("2026-07", 1400000),
                        new OfdResponse.MonthRevenue("2026-08", 1460000),
                        new OfdResponse.MonthRevenue("2026-09", 1480000)),
                0.7);

        when(mocksClient.egrul(inn)).thenReturn(egrul);
        when(mocksClient.bki(inn)).thenReturn(bki);
        when(mocksClient.fnsDebts(inn)).thenReturn(fns);
        when(mocksClient.ofdRevenue(inn)).thenReturn(Optional.of(ofd));
        when(mocksClient.marketplaceSales(inn)).thenReturn(Optional.empty());
        when(mocksClient.acquiringTurnover(inn)).thenReturn(Optional.empty());

        EnrichmentResult result = enrichmentService.enrich(inn, true);

        assertEquals(inn, result.inn());
        assertEquals(FIXED_DATE, result.asOf());
        assertEquals(4, result.spots().size());
        assertEquals(1, result.availableRevenueSources().size());
        assertEquals(140666667L, result.availableRevenueSources().get(0).averageMonthlyKopecks());
    }

    @Test
    void createScoreRequestMapsAllFieldsCorrectly() {
        String inn = "7725555556";
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", inn, "ООО «Техснаб»", "OOO", "ACTIVE",
                LocalDate.of(2021, 2, 10),
                new EgrulResponse.Director("Орлов Д. А.", LocalDate.of(2021, 2, 10), false),
                new EgrulResponse.Address("г. Москва, Варшавское ш., 33", false),
                "46.69", 1);

        BkiResponse bki = new BkiResponse(
                "BKI", inn, 730,
                List.of(), 4,
                new BkiResponse.Delinquencies(0, 3, 0), 0);

        FnsResponse fns = new FnsResponse(
                "FNS", inn,
                new FnsResponse.TaxDebt(3412, "RUB", LocalDate.of(2026, 10, 15), null),
                0, "OSN");

        EnrichmentResult enrichment = new EnrichmentResult(inn, egrul, bki, fns, List.of(), FIXED_DATE, List.of());

        ScoreRequest req = enrichmentService.createScoreRequest(
                enrichment,
                ProductCode.WORKING_CAPITAL,
                80000000L,
                12,
                120000000L,
                Stability.CLEAN,
                Purpose.MATCH,
                Collateral.GUARANTEE,
                0L);

        assertEquals(FIXED_DATE, req.asOf());
        assertEquals(ProductCode.WORKING_CAPITAL, req.productCode());
        assertEquals(80000000L, req.amount());
        assertEquals(12, req.termMonths());
        assertEquals("ACTIVE", req.egrulStatus());
        assertEquals(LocalDate.of(2021, 2, 10), req.registeredAt());
        assertEquals(LocalDate.of(2021, 2, 10), req.directorSince());
        assertFalse(req.massAddress());
        assertEquals(730, req.creditScore());
        assertEquals(0, req.delinq12m());
        assertEquals(0, req.bkiCurrentOverdue());
        assertEquals(341200L, req.taxDebt());
        assertEquals(0, req.unfiledReports());
        assertEquals(120000000L, req.monthlyRevenue());
        assertEquals(Stability.CLEAN, req.stability());
        assertEquals(Purpose.MATCH, req.purpose());
        assertEquals(Collateral.GUARANTEE, req.collateral());
        assertEquals(0L, req.bankPayment());
        assertTrue(req.bkiCredits().isEmpty());
    }

    @Test
    void createScoreRequestPassesBankPaymentAndOpenBkiCreditsWithDebtInKopecks() {
        String inn = "590234567822";
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", inn, "ИП Петров Сергей Николаевич", "IP", "ACTIVE",
                LocalDate.of(2024, 3, 1),
                new EgrulResponse.Director("Петров С. Н.", LocalDate.of(2024, 3, 1), false),
                new EgrulResponse.Address("г. Пермь, ул. Ленина, 40", false),
                "41.20", 1);
        BkiResponse bki = new BkiResponse(
                "BKI", inn, 655,
                List.of(new BkiResponse.Credit("LOAN", "ПАО «Банк»", 400000, 150000, LocalDate.of(2025, 2, 20)),
                        new BkiResponse.Credit("OVERDRAFT", "АО «Банк»", 300000, 0, LocalDate.of(2024, 3, 11)),
                        new BkiResponse.Credit("CREDIT_CARD", "АО «Банк»", 150000, 40000, LocalDate.of(2024, 11, 5))),
                1, new BkiResponse.Delinquencies(0, 12, 0), 2);
        FnsResponse fns = new FnsResponse("FNS", inn, new FnsResponse.TaxDebt(0, "RUB", LocalDate.of(2026, 10, 15),
                null), 0, "USN");
        EnrichmentResult enrichment = new EnrichmentResult(inn, egrul, bki, fns, List.of(), FIXED_DATE, List.of());

        ScoreRequest req = enrichmentService.createScoreRequest(enrichment, ProductCode.WORKING_CAPITAL, 300000000L,
                12, 80000000L, Stability.DOCUMENT, Purpose.MATCH, null, 7500000L);

        assertEquals(7500000L, req.bankPayment());
        assertEquals(List.of(new BkiCredit("LOAN", 15000000L), new BkiCredit("CREDIT_CARD", 4000000L)),
                req.bkiCredits());
    }

    @Test
    void persistCompanySnapshotCallsRepository() {
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", "7701234560", "ООО «Кофейня»", "OOO", "ACTIVE",
                LocalDate.of(2019, 4, 15),
                new EgrulResponse.Director("Смирнова А. В.", LocalDate.of(2019, 4, 15), false),
                new EgrulResponse.Address("г. Москва, ул. Покровка, 12", false),
                "56.30", 2);

        UUID expectedId = UUID.randomUUID();
        when(companyRepository.upsertCompany(eq("7701234560"), eq("ООО «Кофейня»"), eq(LegalForm.OOO), any()))
                .thenReturn(expectedId);

        UUID id = enrichmentService.persistCompanySnapshot(egrul);
        assertEquals(expectedId, id);
        verify(companyRepository).upsertCompany(eq("7701234560"), eq("ООО «Кофейня»"), eq(LegalForm.OOO), any());
    }

    @Test
    void recordConsentDelegatesToRepository() {
        UUID appId = UUID.randomUUID();
        enrichmentService.recordConsent(appId, SourceCode.BKI, true);
        verify(applicationSourceRepository).recordConsent(appId, SourceCode.BKI, true);
    }

    @Test
    void revenueCalculations() {
        MarketplaceResponse wb = new MarketplaceResponse(
                "MARKETPLACE", "590987654351",
                List.of(
                        new MarketplaceResponse.MonthSales("2026-04", 480000, 360000, 11),
                        new MarketplaceResponse.MonthSales("2026-05", 510000, 380000, 12),
                        new MarketplaceResponse.MonthSales("2026-06", 500000, 375000, 11),
                        new MarketplaceResponse.MonthSales("2026-07", 520000, 390000, 13),
                        new MarketplaceResponse.MonthSales("2026-08", 620000, 465000, 12),
                        new MarketplaceResponse.MonthSales("2026-09", 570000, 430000, 14)),
                4.6, 14);

        long wbAverage = EnrichmentService.calculateMarketplaceAverageKopecks(wb);
        assertEquals(40000000L, wbAverage);

        AcquiringResponse acq = new AcquiringResponse(
                "ACQUIRING", "7725555556",
                List.of(
                        new AcquiringResponse.MonthTurnover("2025-10", 1150000, 240),
                        new AcquiringResponse.MonthTurnover("2025-11", 1190000, 248),
                        new AcquiringResponse.MonthTurnover("2025-12", 1260000, 262),
                        new AcquiringResponse.MonthTurnover("2026-01", 1180000, 246),
                        new AcquiringResponse.MonthTurnover("2026-02", 1210000, 252),
                        new AcquiringResponse.MonthTurnover("2026-03", 1230000, 256),
                        new AcquiringResponse.MonthTurnover("2026-04", 1170000, 244),
                        new AcquiringResponse.MonthTurnover("2026-05", 1220000, 254),
                        new AcquiringResponse.MonthTurnover("2026-06", 1190000, 248),
                        new AcquiringResponse.MonthTurnover("2026-07", 1240000, 258),
                        new AcquiringResponse.MonthTurnover("2026-08", 1180000, 246),
                        new AcquiringResponse.MonthTurnover("2026-09", 1180000, 246)),
                4800);

        long acqAverage = EnrichmentService.calculateAcquiringAverageKopecks(acq);
        assertEquals(120000000L, acqAverage);

        List<RevenueSourceData> sources = List.of(
                new RevenueSourceData(SourceCode.MARKETPLACE, wbAverage, wb),
                new RevenueSourceData(SourceCode.ACQUIRING, acqAverage, acq));

        Long maxKopecks = enrichmentService.calculateMonthlyRevenueKopecks(sources);
        assertNotNull(maxKopecks);
        assertEquals(120000000L, maxKopecks);

        assertNull(enrichmentService.calculateMonthlyRevenueKopecks(List.of()));
    }
}
