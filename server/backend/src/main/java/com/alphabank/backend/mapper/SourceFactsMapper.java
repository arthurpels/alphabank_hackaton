package com.alphabank.backend.mapper;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.rule.SpotRules;

@Component
public class SourceFactsMapper {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String IP = "IP";
    private static final String ACTIVE = "ACTIVE";

    public String creditHistory(BkiResponse bki) {
        int delinquencies = bki.delinquencies() == null ? 0 : bki.delinquencies().last12m();
        return "кредитный рейтинг " + bki.creditScore()
                + (delinquencies == 0 ? ", просрочек за 12 мес. нет" : ", просрочек за 12 мес.: " + delinquencies);
    }

    public String currentOverdue(BkiResponse bki) {
        int current = bki.delinquencies() == null ? 0 : bki.delinquencies().current();
        return current == 0 ? "текущих просрочек нет" : "текущих просрочек: " + current;
    }

    public String registration(EgrulResponse egrul) {
        return (isIp(egrul) ? "ИП зарегистрирован " : "компания зарегистрирована ")
                + egrul.registeredAt().format(DATE);
    }

    public String company(EgrulResponse egrul) {
        return egrul.name() + ", ИНН " + egrul.inn();
    }

    public String status(EgrulResponse egrul) {
        return ACTIVE.equalsIgnoreCase(egrul.status()) ? "статус: действует" : "статус: " + egrul.status();
    }

    public String director(EgrulResponse egrul) {
        String since = egrul.director().appointedAt() == null ? ""
                : " с " + egrul.director().appointedAt().format(DATE);
        return (isIp(egrul) ? "предприниматель " : "руководитель ") + egrul.director().fio() + since;
    }

    public String address(EgrulResponse egrul) {
        return "адрес: " + egrul.address().value();
    }

    public String taxDebt(FnsResponse fns) {
        long debt = fns.taxDebt() == null ? 0 : fns.taxDebt().amount();
        return debt == 0 ? "долга перед ФНС нет" : "долг перед ФНС " + SpotRules.formatRubles(debt) + " ₽";
    }

    public String unfiledReports(FnsResponse fns) {
        return fns.unfiledReports() == 0 ? "несданных отчётов нет" : "несданных отчётов: " + fns.unfiledReports();
    }

    public String revenue(RevenueSourceData source) {
        return "выручка в среднем " + rubles(source.averageMonthlyKopecks()) + " ₽ в месяц";
    }

    public String rubles(long kopecks) {
        return SpotRules.formatRubles(kopecks / 100);
    }

    private static boolean isIp(EgrulResponse egrul) {
        return IP.equalsIgnoreCase(egrul.legalForm());
    }
}
