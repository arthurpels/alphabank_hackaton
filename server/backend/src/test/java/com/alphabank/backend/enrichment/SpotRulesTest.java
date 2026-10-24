package com.alphabank.backend.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.SpotDraft;
import com.alphabank.backend.enrichment.rule.SpotRules;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotType;

class SpotRulesTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 23);

    @Test
    void coffeeSpotSetMatchesSyntheticData() {
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", "7701234560", "ООО «Кофейня»", "OOO", "ACTIVE",
                LocalDate.of(2019, 4, 15),
                new EgrulResponse.Director("Смирнова А. В.", LocalDate.of(2019, 4, 15), false),
                new EgrulResponse.Address("г. Москва, ул. Покровка, 12", false),
                "56.30", 2);

        BkiResponse bki = new BkiResponse(
                "BKI", "7701234560", 712,
                List.of(new BkiResponse.Credit("OVERDRAFT", "АО «Банк»", 300000, 0, LocalDate.of(2024, 3, 11))),
                3,
                new BkiResponse.Delinquencies(0, 5, 0),
                1);

        FnsResponse fns = new FnsResponse(
                "FNS", "7701234560",
                new FnsResponse.TaxDebt(0, "RUB", LocalDate.of(2026, 10, 15), null),
                0, "USN");

        List<RevenueSourceData> available = List.of(
                new RevenueSourceData(SourceCode.OFD, 140666667L, null),
                new RevenueSourceData(SourceCode.ACQUIRING, 97000000L, null));

        List<SpotDraft> spots = SpotRules.evaluateInitialSpots(egrul, bki, fns, available, AS_OF);

        assertEquals(4, spots.size());

        SpotDraft s1 = spots.get(0);
        assertEquals(SpotType.NO_DATA, s1.type());
        assertEquals("Продажи через кассу не видны", s1.title());
        assertEquals("debt_load", s1.scoringFactor());
        assertEquals(3.5, s1.pricePp());
        assertEquals(CloseMethod.CONNECT_SOURCE, s1.closeMethod());
        assertEquals(SourceCode.OFD, s1.source());
        assertNull(s1.removable());

        SpotDraft s2 = spots.get(1);
        assertEquals(SpotType.NO_DATA, s2.type());
        assertEquals("Не знаем, стабильна ли выручка — закроется, когда подключите источник", s2.title());
        assertEquals("revenue_stability", s2.scoringFactor());
        assertEquals(2.5, s2.pricePp());
        assertEquals(CloseMethod.CONNECT_SOURCE, s2.closeMethod());
        assertEquals(SourceCode.OFD, s2.source());

        SpotDraft s3 = spots.get(2);
        assertEquals(SpotType.NO_DATA, s3.type());
        assertEquals("На что именно нужны деньги", s3.title());
        assertEquals("purpose", s3.scoringFactor());
        assertEquals(1.5, s3.pricePp());
        assertEquals(CloseMethod.ANSWER, s3.closeMethod());
        assertNull(s3.source());

        SpotDraft s4 = spots.get(3);
        assertEquals(SpotType.COLLATERAL, s4.type());
        assertEquals("Залог или поручительство — по желанию", s4.title());
        assertEquals("collateral", s4.scoringFactor());
        assertEquals(1.5, s4.pricePp());
        assertEquals(CloseMethod.ADD_COLLATERAL, s4.closeMethod());
        assertNull(s4.source());
    }

    @Test
    void petrovSpotSetMatchesSyntheticData() {
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", "590234567822", "ИП Петров Сергей Николаевич", "IP", "ACTIVE",
                LocalDate.of(2024, 3, 1),
                new EgrulResponse.Director("Петров С. Н.", LocalDate.of(2024, 3, 1), false),
                new EgrulResponse.Address("г. Пермь, ул. Ленина, 40", false),
                "41.20", 1);

        BkiResponse bki = new BkiResponse(
                "BKI", "590234567822", 655,
                List.of(new BkiResponse.Credit("LOAN", "ПАО «Банк»", 400000, 150000, LocalDate.of(2025, 2, 20))),
                1,
                new BkiResponse.Delinquencies(0, 12, 0),
                2);

        FnsResponse fns = new FnsResponse(
                "FNS", "590234567822",
                new FnsResponse.TaxDebt(0, "RUB", LocalDate.of(2026, 10, 15), null),
                0, "USN");

        List<RevenueSourceData> available = List.of();

        List<SpotDraft> spots = SpotRules.evaluateInitialSpots(egrul, bki, fns, available, AS_OF);

        assertEquals(4, spots.size());
        assertEquals(SpotType.NO_DATA, spots.get(0).type());
        assertEquals("Не видим выручку: подключите кассу, маркетплейс или эквайринг", spots.get(0).title());
        assertNull(spots.get(0).source());
        assertEquals(CloseMethod.CONNECT_SOURCE, spots.get(0).closeMethod());

        assertEquals(SpotType.NO_DATA, spots.get(1).type());
        assertEquals("Не знаем, стабильна ли выручка", spots.get(1).title());
        assertNull(spots.get(1).source());

        assertEquals(SpotType.NO_DATA, spots.get(2).type());
        assertEquals("На что именно нужны деньги", spots.get(2).title());

        assertEquals(SpotType.COLLATERAL, spots.get(3).type());
        assertEquals("Залог или поручительство — по желанию", spots.get(3).title());
    }

    @Test
    void kuznetsovaSpotSetMatchesSyntheticData() {
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", "590987654351", "ИП Кузнецова Мария Игоревна", "IP", "ACTIVE",
                LocalDate.of(2024, 5, 20),
                new EgrulResponse.Director("Кузнецова М. И.", LocalDate.of(2024, 5, 20), false),
                new EgrulResponse.Address("г. Пермь, ул. Сибирская, 15", false),
                "47.91", 1);

        BkiResponse bki = new BkiResponse(
                "BKI", "590987654351", 680,
                List.of(new BkiResponse.Credit("CREDIT_CARD", "АО «Банк»", 150000, 40000, LocalDate.of(2024, 11, 5))),
                0,
                new BkiResponse.Delinquencies(0, 0, 0),
                2);

        FnsResponse fns = new FnsResponse(
                "FNS", "590987654351",
                new FnsResponse.TaxDebt(0, "RUB", LocalDate.of(2026, 10, 15), null),
                0, "USN");

        List<RevenueSourceData> available = List.of(
                new RevenueSourceData(SourceCode.MARKETPLACE, 40000000L, null));

        List<SpotDraft> spots = SpotRules.evaluateInitialSpots(egrul, bki, fns, available, AS_OF);

        assertEquals(4, spots.size());
        assertEquals("Продажи на маркетплейсе не видны", spots.get(0).title());
        assertEquals(SourceCode.MARKETPLACE, spots.get(0).source());
        assertEquals("Не знаем, стабильна ли выручка", spots.get(1).title());
        assertEquals(SourceCode.MARKETPLACE, spots.get(1).source());
    }

    @Test
    void techsnabSpotSetMatchesSyntheticData() {
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", "7725555556", "ООО «Техснаб»", "OOO", "ACTIVE",
                LocalDate.of(2021, 2, 10),
                new EgrulResponse.Director("Орлов Д. А.", LocalDate.of(2021, 2, 10), false),
                new EgrulResponse.Address("г. Москва, Варшавское ш., 33", false),
                "46.69", 1);

        BkiResponse bki = new BkiResponse(
                "BKI", "7725555556", 730,
                List.of(), 4,
                new BkiResponse.Delinquencies(0, 3, 0), 0);

        FnsResponse fns = new FnsResponse(
                "FNS", "7725555556",
                new FnsResponse.TaxDebt(3412, "RUB", LocalDate.of(2026, 10, 15), null),
                0, "OSN");

        List<RevenueSourceData> available = List.of(
                new RevenueSourceData(SourceCode.ACQUIRING, 120000000L, null));

        List<SpotDraft> spots = SpotRules.evaluateInitialSpots(egrul, bki, fns, available, AS_OF);

        assertEquals(5, spots.size());

        SpotDraft s0 = spots.get(0);
        assertEquals(SpotType.STOP_FACTOR, s0.type());
        assertEquals("Долг в ФНС 3 412 ₽ — без погашения будет отказ", s0.title());
        assertEquals(CloseMethod.PAY, s0.closeMethod());
        assertEquals(SourceCode.FNS, s0.source());
        assertTrue(s0.removable());
        assertNull(s0.pricePp());

        SpotDraft s1 = spots.get(1);
        assertEquals(SpotType.NO_DATA, s1.type());
        assertEquals("Обороты по эквайрингу не видны", s1.title());
        assertEquals(SourceCode.ACQUIRING, s1.source());
    }

    @Test
    void odnodnevkaSpotSetMatchesSyntheticData() {
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", "7719999994", "ООО «Однодневка»", "OOO", "ACTIVE",
                LocalDate.of(2025, 8, 1),
                new EgrulResponse.Director("Иванов И. И.", LocalDate.of(2026, 9, 15), false),
                new EgrulResponse.Address("г. Москва, ул. Примерная, 1", true),
                "46.90", 1);

        BkiResponse bki = new BkiResponse(
                "BKI", "7719999994", 600,
                List.of(), 0,
                new BkiResponse.Delinquencies(0, 0, 0), 3);

        FnsResponse fns = new FnsResponse(
                "FNS", "7719999994",
                new FnsResponse.TaxDebt(0, "RUB", LocalDate.of(2026, 10, 15), null),
                0, "OSN");

        List<RevenueSourceData> available = List.of(
                new RevenueSourceData(SourceCode.ACQUIRING, 20000000L, null));

        List<SpotDraft> spots = SpotRules.evaluateInitialSpots(egrul, bki, fns, available, AS_OF);

        assertEquals(2, spots.size());

        SpotDraft s0 = spots.get(0);
        assertEquals(SpotType.STOP_FACTOR, s0.type());
        assertEquals("Директор назначен меньше 3 месяцев назад", s0.title());
        assertNull(s0.closeMethod());
        assertEquals(SourceCode.EGRUL, s0.source());
        assertFalse(s0.removable());

        SpotDraft s1 = spots.get(1);
        assertEquals(SpotType.STOP_FACTOR, s1.type());
        assertEquals("Адрес регистрации массовый", s1.title());
        assertNull(s1.closeMethod());
        assertEquals(SourceCode.EGRUL, s1.source());
        assertFalse(s1.removable());
    }

    @Test
    void stopFactorsDetection() {
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", "7700000001", "ООО «Тест»", "OOO", "LIQUIDATING",
                LocalDate.of(2026, 8, 1),
                new EgrulResponse.Director("Тестов Т. Т.", LocalDate.of(2026, 8, 1), false),
                new EgrulResponse.Address("г. Москва", false),
                "10.00", 1);

        BkiResponse bki = new BkiResponse(
                "BKI", "7700000001", 550,
                List.of(), 0,
                new BkiResponse.Delinquencies(5000, 15, 2), 7);

        FnsResponse fns = new FnsResponse(
                "FNS", "7700000001",
                new FnsResponse.TaxDebt(10000, "RUB", LocalDate.of(2026, 10, 15), null),
                1, "OSN");

        List<SpotDraft> stopFactors = SpotRules.evaluateStopFactors(egrul, bki, fns, AS_OF);

        assertTrue(stopFactors.stream().anyMatch(s -> s.title().contains("Компания в статусе LIQUIDATING")));
        assertTrue(stopFactors.stream().anyMatch(s -> s.title().contains("Компания зарегистрирована менее 6 месяцев назад")));
        assertTrue(stopFactors.stream().anyMatch(s -> s.title().contains("Есть текущая просрочка по кредитам")
                && s.type() == SpotType.STOP_FACTOR
                && Boolean.FALSE.equals(s.removable())
                && s.closeMethod() == null));
        assertTrue(stopFactors.stream().anyMatch(s -> s.title().contains("Долг в ФНС 10 000 ₽")));
    }

    @Test
    void bkiHistoryDoesNotCreateSpots() {
        EgrulResponse egrul = new EgrulResponse(
                "EGRUL", "7700000002", "ООО «Тест»", "OOO", "ACTIVE",
                LocalDate.of(2019, 1, 1),
                new EgrulResponse.Director("Тестов Т. Т.", LocalDate.of(2019, 1, 1), false),
                new EgrulResponse.Address("г. Москва", false),
                "10.00", 1);

        BkiResponse bki = new BkiResponse(
                "BKI", "7700000002", 550,
                List.of(), 0,
                new BkiResponse.Delinquencies(0, 15, 2), 7);

        FnsResponse fns = new FnsResponse(
                "FNS", "7700000002",
                new FnsResponse.TaxDebt(0, "RUB", LocalDate.of(2026, 10, 15), null),
                0, "USN");

        List<RevenueSourceData> available = List.of(
                new RevenueSourceData(SourceCode.ACQUIRING, 100000000L, null));

        List<SpotDraft> spots = SpotRules.evaluateInitialSpots(egrul, bki, fns, available, AS_OF);

        assertTrue(spots.stream().noneMatch(s -> s.type() == SpotType.ANOMALY));
        assertTrue(spots.stream().noneMatch(s -> "credit_history".equals(s.scoringFactor())));
    }
}
