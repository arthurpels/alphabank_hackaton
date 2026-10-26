package com.alphabank.backend.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.alphabank.backend.dto.response.AnswerOptionDto;
import com.alphabank.backend.dto.response.SummaryItemDto;
import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.enrichment.client.MockNotFoundException;
import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.enrichment.dto.AcquiringResponse;
import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.MarketplaceResponse;
import com.alphabank.backend.enrichment.dto.OfdResponse;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.scoringclient.AnomalyClient;
import com.alphabank.backend.scoringclient.MlVersionClient;
import com.alphabank.backend.scoringclient.OfferGridClient;
import com.alphabank.backend.scoringclient.RulesClient;
import com.alphabank.backend.scoringclient.ScoringClient;
import com.alphabank.backend.scoringclient.SummaryClient;
import com.alphabank.backend.scoringclient.dto.AnomaliesResponse;
import com.alphabank.backend.scoringclient.dto.Anomaly;
import com.alphabank.backend.scoringclient.dto.Factor;
import com.alphabank.backend.scoringclient.dto.Offer;
import com.alphabank.backend.scoringclient.dto.OfferGridResponse;
import com.alphabank.backend.scoringclient.dto.Policy;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.scoringclient.dto.SeriesPoint;
import com.alphabank.backend.scoringclient.dto.StopFactor;
import com.alphabank.backend.scoringclient.dto.Thresholds;
import com.alphabank.backend.scoringclient.enums.Collateral;
import com.alphabank.backend.scoringclient.enums.FactorCode;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.Stability;
import com.alphabank.backend.scoringclient.enums.StopFactorCode;

@TestConfiguration
public class FakeExternalServicesConfig {

    public static final String COFFEE_INN = "7701234560";
    public static final String TECHSNAB_INN = "7725555556";
    public static final String BUILDER_INN = "590234567822";
    public static final String SHELL_INN = "7719999994";

    private static final List<String> MONTHS = List.of("2025-10", "2025-11", "2025-12", "2026-01", "2026-02",
            "2026-03", "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09");
    private static final List<Long> COFFEE_OFD = List.of(1410000L, 1380000L, 1560000L, 1450000L, 1520000L, 830000L,
            1490000L, 1470000L, 1430000L, 1400000L, 1460000L, 1480000L);

    public static final long POLICY_MAX_AMOUNT = 500_000_000L;
    public static final String ENGINE_VERSION = "test-engine";

    private final Set<String> paidInns = new HashSet<>();

    @Bean
    @Primary
    public MocksClient fakeMocksClient() {
        return new MocksClient() {
            @Override
            public EgrulResponse egrul(String inn) {
                if (COFFEE_INN.equals(inn)) {
                    return new EgrulResponse("EGRUL", inn, "ООО «Кофейня»", "OOO", "ACTIVE",
                            LocalDate.of(2019, 4, 15),
                            new EgrulResponse.Director("Смирнова А. В.", LocalDate.of(2019, 4, 15), false),
                            new EgrulResponse.Address("г. Москва, ул. Покровка, 12", false), "56.30", 2);
                }
                if (TECHSNAB_INN.equals(inn)) {
                    return new EgrulResponse("EGRUL", inn, "ООО «Техснаб»", "OOO", "ACTIVE",
                            LocalDate.of(2021, 2, 10),
                            new EgrulResponse.Director("Орлов Д. А.", LocalDate.of(2021, 2, 10), false),
                            new EgrulResponse.Address("г. Москва, Варшавское ш., 33", false), "46.69", 1);
                }
                if (BUILDER_INN.equals(inn)) {
                    return new EgrulResponse("EGRUL", inn, "ИП Петров Сергей Николаевич", "IP", "ACTIVE",
                            LocalDate.of(2024, 3, 1),
                            new EgrulResponse.Director("Петров С. Н.", LocalDate.of(2024, 3, 1), false),
                            new EgrulResponse.Address("г. Пермь, ул. Ленина, 40", false), "41.20", 1);
                }
                if (SHELL_INN.equals(inn)) {
                    return new EgrulResponse("EGRUL", inn, "ООО «Однодневка»", "OOO", "ACTIVE",
                            LocalDate.of(2025, 8, 1),
                            new EgrulResponse.Director("Иванов И. И.", LocalDate.of(2026, 9, 1), false),
                            new EgrulResponse.Address("г. Москва, ул. Тверская, 7", true), "46.90", 1);
                }
                throw new MockNotFoundException("ИНН " + inn + " не найден в источнике");
            }

            @Override
            public BkiResponse bki(String inn) {
                int score = TECHSNAB_INN.equals(inn) ? 730 : BUILDER_INN.equals(inn) ? 655 : 712;
                return new BkiResponse("BKI", inn, score, List.of(), 3, new BkiResponse.Delinquencies(0, 5, 0), 1);
            }

            @Override
            public FnsResponse fnsDebts(String inn) {
                long debt = TECHSNAB_INN.equals(inn) && !paidInns.contains(inn) ? 3412 : 0;
                return new FnsResponse("FNS", inn,
                        new FnsResponse.TaxDebt(debt, "RUB", LocalDate.of(2026, 10, 15), null), 0, "USN");
            }

            @Override
            public Optional<OfdResponse> ofdRevenue(String inn, int requestedMonths) {
                if (!COFFEE_INN.equals(inn)) {
                    return Optional.empty();
                }
                List<OfdResponse.MonthRevenue> months = new ArrayList<>();
                for (int i = Math.max(0, MONTHS.size() - requestedMonths); i < MONTHS.size(); i++) {
                    months.add(new OfdResponse.MonthRevenue(MONTHS.get(i), COFFEE_OFD.get(i)));
                }
                return Optional.of(new OfdResponse("OFD", inn, months, 0.7));
            }

            @Override
            public Optional<MarketplaceResponse> marketplaceSales(String inn, int months) {
                return Optional.empty();
            }

            @Override
            public Optional<AcquiringResponse> acquiringTurnover(String inn, int months) {
                return Optional.empty();
            }

            @Override
            public void ofdConnect(String inn) {
                if (!COFFEE_INN.equals(inn)) {
                    throw new MockNotFoundException("Источник OFD не заведён для ИНН " + inn);
                }
            }

            @Override
            public void marketplaceConnect(String inn) {
                throw new MockNotFoundException("Источник MARKETPLACE не заведён для ИНН " + inn);
            }

            @Override
            public void acquiringConnect(String inn) {
                throw new MockNotFoundException("Источник ACQUIRING не заведён для ИНН " + inn);
            }

            @Override
            public void fnsPay(String inn) {
                paidInns.add(inn);
            }

            @Override
            public void reset() {
                paidInns.clear();
            }
        };
    }

    @Bean
    @Primary
    public ScoringClient fakeScoringClient() {
        return FakeExternalServicesConfig::score;
    }

    @Bean
    @Primary
    public OfferGridClient fakeOfferGridClient() {
        Policy passed = new Policy("PTI", 0.2, 0.3, true, true);
        return request -> new OfferGridResponse("2026-10-23.draft1", List.of(
                new Offer(request.productCode(), request.amount() * 6 / 10, 36, null, 19.0, 20.0, passed),
                new Offer(ProductCode.OVERDRAFT, 12_000_000L, 12, null, 22.0, 22.0, passed)));
    }

    @Bean
    @Primary
    public CountingSummaryClient fakeSummaryClient() {
        return new CountingSummaryClient();
    }

    @Bean
    @Primary
    public AnomalyClient fakeAnomalyClient() {
        return request -> {
            List<Anomaly> anomalies = new ArrayList<>();
            request.series().forEach(series -> {
                List<SeriesPoint> points = series.points();
                for (int i = 1; i < points.size(); i++) {
                    long prev = points.get(i - 1).value();
                    long cur = points.get(i).value();
                    if (cur < prev * 0.7) {
                        int pct = (int) Math.round((1 - (double) cur / prev) * 100);
                        anomalies.add(new Anomaly("REVENUE_DROP", series.source(), points.get(i).month(), -pct,
                                "Почему в марте выручка упала на " + pct + " %",
                                "Пока нет объяснения — считаем это риском. Что случилось?",
                                List.of(new AnswerOptionDto("RENOVATION", "Был ремонт"),
                                        new AnswerOptionDto("SEASONAL", "Сезонный спад"),
                                        new AnswerOptionDto("CLIENT_LOST", "Ушёл крупный клиент"))));
                    }
                }
            });
            return new AnomaliesResponse(anomalies);
        };
    }

    @Bean
    @Primary
    public MlVersionClient fakeMlVersionClient() {
        return () -> Optional.of(ENGINE_VERSION);
    }

    @Bean
    @Primary
    public RulesClient fakeRulesClient() {
        return () -> {
            com.fasterxml.jackson.databind.node.ObjectNode rules =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            rules.put("rulesVersion", "2026-10-23.draft1");
            rules.put("calcRatePct", 15.0);
            rules.putArray("products");
            rules.putArray("factors");
            rules.putArray("stopFactors");
            return rules;
        };
    }

    @Bean
    @Primary
    public CountingAnswerClient fakeAnswerClient() {
        return new CountingAnswerClient();
    }

    @Bean
    @Primary
    public CountingPurposeClient fakePurposeClient() {
        return new CountingPurposeClient();
    }

    private static ScoreResponse score(ScoreRequest request) {
        double base = 22.0 + (request.creditScore() != null && request.creditScore() >= 700 ? -1.0 : 0.0) - 0.5;
        double lo = base;
        double hi = base;
        List<Factor> factors = new ArrayList<>();
        if (request.monthlyRevenue() == null) {
            lo -= 1.5;
            hi += 2.0;
            factors.add(unknown(FactorCode.DEBT_LOAD, -1.5, 2.0));
        } else {
            lo -= 0.5;
            hi -= 0.5;
            factors.add(known(FactorCode.DEBT_LOAD, -0.5));
        }
        if (request.stability() == null || request.stability() == Stability.ANOMALY_OPEN) {
            hi += 2.5;
            factors.add(unknown(FactorCode.REVENUE_STABILITY, 0.0, 2.5));
        } else if (request.stability() == Stability.DOCUMENT) {
            lo += 1.0;
            hi += 1.0;
            factors.add(known(FactorCode.REVENUE_STABILITY, 1.0));
        } else {
            factors.add(known(FactorCode.REVENUE_STABILITY, 0.0));
        }
        if (request.purpose() == null) {
            lo -= 0.5;
            hi += 1.0;
            factors.add(unknown(FactorCode.PURPOSE, -0.5, 1.0));
        } else {
            double value = request.purpose() == Purpose.MATCH ? -0.5 : 0.5;
            lo += value;
            hi += value;
            factors.add(known(FactorCode.PURPOSE, value));
        }
        if (request.collateral() == null) {
            lo -= 1.5;
            factors.add(unknown(FactorCode.COLLATERAL, -1.5, 0.0));
        } else {
            double value = request.collateral() == Collateral.DECLINED ? 0.0 : -1.5;
            lo += value;
            hi += value;
            factors.add(known(FactorCode.COLLATERAL, value));
        }
        List<StopFactor> stops = request.taxDebt() > 0
                ? List.of(new StopFactor(StopFactorCode.TAX_DEBT, true, "PAY")) : List.of();
        Thresholds thresholds = request.productCode() == ProductCode.OVERDRAFT
                ? new Thresholds(22.0, 26.0) : new Thresholds(20.0, 24.0);
        return new ScoreResponse("2026-10-23.draft1", request.productCode(), lo, hi, thresholds,
                factors, stops, new Policy(null, null, null, true, request.amount() <= POLICY_MAX_AMOUNT), hi <= 20.0);
    }

    private static Factor known(FactorCode code, double value) {
        return new Factor(code, code.name(), true, true, value, null, null, null);
    }

    private static Factor unknown(FactorCode code, double best, double worst) {
        return new Factor(code, code.name(), true, false, null, best, worst, worst - best);
    }
}
