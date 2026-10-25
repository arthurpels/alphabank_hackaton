package com.alphabank.backend.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.response.FactRefDto;
import com.alphabank.backend.dto.response.SummaryItemDto;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;
import com.alphabank.backend.scoringclient.dto.SummarySpot;

@Component
public class TemplateSummaryFactory {

    private static final int MAX_ITEMS = 5;
    private static final int MIN_ITEMS = 3;
    private static final Map<String, String> SOURCE_TITLES = Map.of(
            "BKI", "БКИ",
            "EGRUL", "ЕГРЮЛ",
            "FNS", "ФНС",
            "OFD", "ОФД",
            "MARKETPLACE", "Маркетплейс",
            "ACQUIRING", "Эквайринг",
            "REPUTATION", "Репутация",
            "CLIENT", "Клиент",
            "DOCUMENT", "Документ");

    public List<SummaryItemDto> create(SummaryRequest request) {
        List<SummaryItemDto> items = new ArrayList<>();
        items.add(new SummaryItemDto(forkText(request), List.of()));
        request.spots().stream()
                .filter(spot -> spot.status() != SpotStatus.CLOSED)
                .sorted(Comparator.comparing((SummarySpot spot) -> spot.pricePp() == null ? 0 : spot.pricePp(),
                        Comparator.reverseOrder()).thenComparing(SummarySpot::title))
                .limit(MAX_ITEMS - 1L)
                .forEach(spot -> items.add(new SummaryItemDto(spotText(spot), factsForSpot(request, spot))));
        Map<String, List<FactRefDto>> unlinked = new LinkedHashMap<>();
        request.facts().stream()
                .filter(fact -> fact.spotId() == null)
                .forEach(fact -> unlinked.computeIfAbsent(fact.source(), key -> new ArrayList<>()).add(fact));
        for (Map.Entry<String, List<FactRefDto>> entry : unlinked.entrySet()) {
            if (items.size() >= MIN_ITEMS) {
                break;
            }
            String title = SOURCE_TITLES.getOrDefault(entry.getKey(), entry.getKey());
            String text = title + ": " + String.join("; ", entry.getValue().stream().map(FactRefDto::label).toList()) + ".";
            items.add(new SummaryItemDto(text, entry.getValue()));
        }
        return items;
    }

    private static List<FactRefDto> factsForSpot(SummaryRequest request, SummarySpot spot) {
        return request.facts().stream().filter(fact -> spot.spotId().equals(fact.spotId())).toList();
    }

    private static String forkText(SummaryRequest request) {
        String fork = "Вилка " + number(request.rateFrom()) + "–" + number(request.rateTo()) + " %";
        String threshold = number(request.approveMax());
        if (request.rateTo() <= request.approveMax()) {
            return fork + " целиком в зоне одобрения (порог " + threshold + " %).";
        }
        if (request.rateFrom() > request.approveMax()) {
            return fork + " целиком выше порога одобрения " + threshold + " %.";
        }
        return fork + " пересекает порог одобрения " + threshold + " % — решение зависит от открытых пятен.";
    }

    private static String spotText(SummarySpot spot) {
        String state = spot.status() == SpotStatus.ON_REVIEW ? "ответ ждёт проверки" : "не закрыто";
        String price = spot.pricePp() == null || spot.pricePp() == 0 ? "." : ", до " + number(spot.pricePp()) + " п.п.";
        String text = "«" + spot.title() + "» — " + state + price;
        if (spot.answer() != null && !spot.answer().isBlank()) {
            text += " Ответ клиента: «" + spot.answer().strip() + "».";
        }
        return text;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
    }
}
