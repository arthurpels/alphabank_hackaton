package com.alphabank.backend.support;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.alphabank.backend.enrichment.client.MockNotFoundException;
import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.enrichment.dto.AcquiringResponse;
import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.MarketplaceResponse;
import com.alphabank.backend.enrichment.dto.OfdResponse;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.scoringclient.ScoringClient;
import com.alphabank.backend.scoringclient.dto.Factor;
import com.alphabank.backend.scoringclient.dto.Policy;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.scoringclient.dto.Thresholds;
import com.alphabank.backend.scoringclient.enums.FactorCode;

@TestConfiguration
public class FakeExternalServicesConfig {

    public static final String COFFEE_INN = "7701234560";

    @Bean
    @Primary
    public MocksClient fakeMocksClient() {
        return new MocksClient() {
            @Override
            public EgrulResponse egrul(String inn) {
                requireCoffee(inn);
                return new EgrulResponse("EGRUL", inn, "ООО «Кофейня»", "OOO", "ACTIVE", LocalDate.of(2019, 4, 15),
                        new EgrulResponse.Director("Смирнова А. В.", LocalDate.of(2019, 4, 15), false),
                        new EgrulResponse.Address("г. Москва, ул. Покровка, 12", false), "56.30", 2);
            }

            @Override
            public BkiResponse bki(String inn) {
                requireCoffee(inn);
                return new BkiResponse("BKI", inn, 712, List.of(), 3, new BkiResponse.Delinquencies(0, 5, 0), 1);
            }

            @Override
            public FnsResponse fnsDebts(String inn) {
                requireCoffee(inn);
                return new FnsResponse("FNS", inn, new FnsResponse.TaxDebt(0, "RUB", LocalDate.of(2026, 10, 15), null),
                        0, "USN");
            }

            @Override
            public Optional<OfdResponse> ofdRevenue(String inn) {
                return Optional.of(new OfdResponse("OFD", inn,
                        List.of(new OfdResponse.MonthRevenue("2026-02", 1520000),
                                new OfdResponse.MonthRevenue("2026-03", 830000)), 0.7));
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
            }

            @Override
            public void marketplaceConnect(String inn) {
            }

            @Override
            public void acquiringConnect(String inn) {
            }

            @Override
            public void fnsPay(String inn) {
            }

            @Override
            public void reset() {
            }

            private void requireCoffee(String inn) {
                if (!COFFEE_INN.equals(inn)) {
                    throw new MockNotFoundException("Источник не нашёл данных для ИНН " + inn);
                }
            }
        };
    }

    @Bean
    @Primary
    public ScoringClient fakeScoringClient() {
        return request -> new ScoreResponse("2026-10-23.draft1", ProductCode.EQUIPMENT, 17.0, 26.0,
                new Thresholds(20.0, 24.0),
                List.of(new Factor(FactorCode.CREDIT_HISTORY, "Кредитная история", true, true, -1.0, null, null, null),
                        new Factor(FactorCode.BUSINESS_AGE, "Возраст бизнеса", true, true, -0.5, null, null, null),
                        new Factor(FactorCode.TAX_DISCIPLINE, "Налоговая дисциплина", true, true, 0.0, null, null, null),
                        new Factor(FactorCode.DEBT_LOAD, "Подтверждённая выручка и долговая нагрузка", true, false,
                                null, -1.5, 2.0, 3.5),
                        new Factor(FactorCode.REVENUE_STABILITY, "Стабильность выручки", true, false,
                                null, 0.0, 2.5, 2.5),
                        new Factor(FactorCode.PURPOSE, "Цель кредита", true, false, null, -0.5, 1.0, 1.5),
                        new Factor(FactorCode.COLLATERAL, "Обеспечение", true, false, null, -1.5, 0.0, 1.5)),
                List.of(), new Policy(null, null, null, true, true), false);
    }
}
