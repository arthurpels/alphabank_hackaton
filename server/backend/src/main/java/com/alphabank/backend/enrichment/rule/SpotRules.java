package com.alphabank.backend.enrichment.rule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.SpotDraft;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotType;

public final class SpotRules {

    private SpotRules() {
    }

    public static List<SpotDraft> evaluateInitialSpots(
            EgrulResponse egrul,
            BkiResponse bki,
            FnsResponse fns,
            List<RevenueSourceData> availableRevenueSources,
            LocalDate asOf) {

        List<SpotDraft> stopFactors = evaluateStopFactors(egrul, bki, fns, asOf);

        boolean hasFatalStopFactor = stopFactors.stream()
                .anyMatch(s -> s.type() == SpotType.STOP_FACTOR && Boolean.FALSE.equals(s.removable()));

        if (hasFatalStopFactor) {
            return List.copyOf(stopFactors);
        }

        List<SpotDraft> spots = new ArrayList<>(stopFactors);
        spots.addAll(evaluateNoDataSpots(availableRevenueSources));

        return List.copyOf(spots);
    }

    public static List<SpotDraft> evaluateStopFactors(
            EgrulResponse egrul,
            BkiResponse bki,
            FnsResponse fns,
            LocalDate asOf) {

        List<SpotDraft> stopFactors = new ArrayList<>();

        if (egrul != null) {
            if (egrul.status() != null && !"ACTIVE".equalsIgnoreCase(egrul.status())) {
                stopFactors.add(new SpotDraft(
                        SpotType.STOP_FACTOR,
                        "Компания в статусе " + egrul.status(),
                        "Кредитование возможно только для действующих компаний",
                        null,
                        null,
                        null,
                        SourceCode.EGRUL,
                        false));
            }

            if (egrul.registeredAt() != null && monthsBetween(egrul.registeredAt(), asOf) < 6) {
                stopFactors.add(new SpotDraft(
                        SpotType.STOP_FACTOR,
                        "Компания зарегистрирована менее 6 месяцев назад",
                        "Минимальный срок работы для получения кредита — 6 месяцев",
                        null,
                        null,
                        null,
                        SourceCode.EGRUL,
                        false));
            }

            if (egrul.director() != null
                    && egrul.director().appointedAt() != null
                    && monthsBetween(egrul.director().appointedAt(), asOf) < 3) {
                stopFactors.add(new SpotDraft(
                        SpotType.STOP_FACTOR,
                        "Директор назначен меньше 3 месяцев назад",
                        "Недавняя смена руководителя — фактор повышенного риска",
                        null,
                        null,
                        null,
                        SourceCode.EGRUL,
                        false));
            }

            if (egrul.address() != null && egrul.address().isMass()) {
                stopFactors.add(new SpotDraft(
                        SpotType.STOP_FACTOR,
                        "Адрес регистрации массовый",
                        "По адресу компании зарегистрировано множество других организаций",
                        null,
                        null,
                        null,
                        SourceCode.EGRUL,
                        false));
            }
        }

        if (fns != null && fns.taxDebt() != null && fns.taxDebt().amount() > 0) {
            stopFactors.add(new SpotDraft(
                    SpotType.STOP_FACTOR,
                    "Долг в ФНС " + formatRubles(fns.taxDebt().amount()) + " ₽ — без погашения будет отказ",
                    "Погасите задолженность — повторим запрос в ФНС автоматически",
                    null,
                    null,
                    CloseMethod.PAY,
                    SourceCode.FNS,
                    true));
        }

        if (bki != null && bki.delinquencies() != null && bki.delinquencies().current() > 0) {
            stopFactors.add(new SpotDraft(
                    SpotType.STOP_FACTOR,
                    "Есть текущая просрочка по кредитам",
                    "Погасите просроченную задолженность для продолжения рассмотрения",
                    null,
                    null,
                    null,
                    SourceCode.BKI,
                    false));
        }

        return List.copyOf(stopFactors);
    }

    public static List<SpotDraft> evaluateNoDataSpots(List<RevenueSourceData> availableRevenueSources) {
        SourceCode primaryRevenueSource = resolvePrimaryRevenueSource(availableRevenueSources);

        List<SpotDraft> list = new ArrayList<>();

        if (primaryRevenueSource == SourceCode.OFD) {
            list.add(new SpotDraft(
                    SpotType.NO_DATA,
                    "Продажи через кассу не видны",
                    "Видим только деньги на счёте. Подключите онлайн-кассу — банк увидит реальную выручку.",
                    "debt_load",
                    3.5,
                    CloseMethod.CONNECT_SOURCE,
                    SourceCode.OFD));
            list.add(new SpotDraft(
                    SpotType.NO_DATA,
                    "Не знаем, стабильна ли выручка — закроется, когда подключите источник",
                    "Закроется само, когда подключите кассу.",
                    "revenue_stability",
                    2.5,
                    CloseMethod.CONNECT_SOURCE,
                    SourceCode.OFD));
        } else if (primaryRevenueSource == SourceCode.MARKETPLACE) {
            list.add(new SpotDraft(
                    SpotType.NO_DATA,
                    "Продажи на маркетплейсе не видны",
                    "Подключите личный кабинет маркетплейса — банк увидит реальные продажи и выплаты.",
                    "debt_load",
                    3.5,
                    CloseMethod.CONNECT_SOURCE,
                    SourceCode.MARKETPLACE));
            list.add(new SpotDraft(
                    SpotType.NO_DATA,
                    "Не знаем, стабильна ли выручка",
                    "Закроется автоматически, когда банк получит данные о выручке.",
                    "revenue_stability",
                    2.5,
                    CloseMethod.CONNECT_SOURCE,
                    SourceCode.MARKETPLACE));
        } else if (primaryRevenueSource == SourceCode.ACQUIRING) {
            list.add(new SpotDraft(
                    SpotType.NO_DATA,
                    "Обороты по эквайрингу не видны",
                    "Подключите торговый или интернет-эквайринг — банк учтёт безналичные обороты.",
                    "debt_load",
                    3.5,
                    CloseMethod.CONNECT_SOURCE,
                    SourceCode.ACQUIRING));
            list.add(new SpotDraft(
                    SpotType.NO_DATA,
                    "Не знаем, стабильна ли выручка",
                    "Закроется автоматически, когда банк получит данные о выручке.",
                    "revenue_stability",
                    2.5,
                    CloseMethod.CONNECT_SOURCE,
                    SourceCode.ACQUIRING));
        } else {
            list.add(new SpotDraft(
                    SpotType.NO_DATA,
                    "Не видим выручку: подключите кассу, маркетплейс или эквайринг",
                    "Подключите онлайн-кассу, маркетплейс или эквайринг — банк увидит выручку и снизит ставку.",
                    "debt_load",
                    3.5,
                    CloseMethod.CONNECT_SOURCE,
                    null));
            list.add(new SpotDraft(
                    SpotType.NO_DATA,
                    "Не знаем, стабильна ли выручка",
                    "Закроется автоматически, когда банк получит данные о выручке.",
                    "revenue_stability",
                    2.5,
                    CloseMethod.CONNECT_SOURCE,
                    null));
        }

        list.add(new SpotDraft(
                SpotType.NO_DATA,
                "На что именно нужны деньги",
                "Опишите своими словами — подберём продукт и учтём цель.",
                "purpose",
                1.5,
                CloseMethod.ANSWER,
                null));

        list.add(new SpotDraft(
                SpotType.COLLATERAL,
                "Залог или поручительство — по желанию",
                "Поручительство собственника или залог сузит вилку",
                "collateral",
                1.5,
                CloseMethod.ADD_COLLATERAL,
                null));

        return List.copyOf(list);
    }

    public static int monthsBetween(LocalDate d1, LocalDate d2) {
        if (d1 == null || d2 == null) {
            return 0;
        }
        int months = (d2.getYear() - d1.getYear()) * 12 + (d2.getMonthValue() - d1.getMonthValue());
        if (d2.getDayOfMonth() < d1.getDayOfMonth()) {
            months--;
        }
        return months;
    }

    public static String formatRubles(long amount) {
        return String.format(Locale.forLanguageTag("ru-RU"), "%,d", amount).replace('\u00A0', ' ');
    }

    private static SourceCode resolvePrimaryRevenueSource(List<RevenueSourceData> availableRevenueSources) {
        if (availableRevenueSources == null || availableRevenueSources.isEmpty()) {
            return null;
        }
        if (availableRevenueSources.stream().anyMatch(s -> s.sourceCode() == SourceCode.OFD)) {
            return SourceCode.OFD;
        }
        if (availableRevenueSources.stream().anyMatch(s -> s.sourceCode() == SourceCode.MARKETPLACE)) {
            return SourceCode.MARKETPLACE;
        }
        if (availableRevenueSources.stream().anyMatch(s -> s.sourceCode() == SourceCode.ACQUIRING)) {
            return SourceCode.ACQUIRING;
        }
        return null;
    }
}
