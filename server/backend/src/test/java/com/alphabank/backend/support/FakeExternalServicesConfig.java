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
import com.alphabank.backend.enrichment.client.MockNotFoundException;
import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.enrichment.dto.AcquiringResponse;
import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.MarketplaceResponse;
import com.alphabank.backend.enrichment.dto.OfdResponse;
import com.alphabank.backend.scoringclient.AnomalyClient;
import com.alphabank.backend.scoringclient.PurposeClient;
import com.alphabank.backend.scoringclient.ScoringClient;
import com.alphabank.backend.scoringclient.dto.AnomaliesResponse;
import com.alphabank.backend.scoringclient.dto.Anomaly;
import com.alphabank.backend.scoringclient.dto.Factor;
import com.alphabank.backend.scoringclient.dto.Policy;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;
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

    private static final List<String> MONTHS = List.of("2025-10", "2025-11", "2025-12", "2026-01", "2026-02",
            "2026-03", "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09");
    private static final List<Long> COFFEE_OFD = List.of(1410000L, 1380000L, 1560000L, 1450000L, 1520000L, 830000L,
            1490000L, 1470000L, 1430000L, 1400000L, 1460000L, 1480000L);

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
                throw new MockNotFoundException("ИНН " + inn + " не найден в источнике");
            }

            @Override
            public BkiResponse bki(String inn) {
                return new BkiResponse("BKI", inn, TECHSNAB_INN.equals(inn) ? 730 : 712, List.of(), 3,
                        new BkiResponse.Delinquencies(0, 5, 0), 1);
            }

            @Override
            public FnsResponse fnsDebts(String inn) {
                long debt = TECHSNAB_INN.equals(inn) && !paidInns.contains(inn) ? 3412 : 0;
                return new FnsResponse("FNS", inn,
                        new FnsResponse.TaxDebt(debt, "RUB", LocalDate.of(2026, 10, 15), null), 0, "USN");
            }

            @Override
            public Optional<OfdResponse> ofdRevenue(String inn) {
                if (!COFFEE_INN.equals(inn)) {
                    return Optional.empty();
                }
                List<OfdResponse.MonthRevenue> months = new ArrayList<>();
                for (int i = 0; i < MONTHS.size(); i++) {
                    months.add(new OfdResponse.MonthRevenue(MONTHS.get(i), COFFEE_OFD.get(i)));
                }
                return Optional.of(new OfdResponse("OFD", inn, months, 0.7));
            }

            @Override
            public Optional<MarketplaceResponse> marketplaceSales(String inn) {
                return Optional.empty();
            }

            @Override
            public Optional<AcquiringResponse> acquiringTurnover(String inn) {
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
    public PurposeClient fakePurposeClient() {
        return request -> {
            String goal = request.goal() == null ? "" : request.goal().toLowerCase();
            Purpose purpose = goal.contains("кофемашин") || goal.contains("витрин") ? Purpose.MATCH
                    : goal.contains("станок") ? Purpose.MISMATCH : null;
            return new PurposeResponse(purpose, true, "KEYWORDS", List.of(), null);
        };
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
        return new ScoreResponse("2026-10-23.draft1", request.productCode(), lo, hi, new Thresholds(20.0, 24.0),
                factors, stops, new Policy(null, null, null, true, true), hi <= 20.0);
    }

    private static Factor known(FactorCode code, double value) {
        return new Factor(code, code.name(), true, true, value, null, null, null);
    }

    private static Factor unknown(FactorCode code, double best, double worst) {
        return new Factor(code, code.name(), true, false, null, best, worst, worst - best);
    }
}
