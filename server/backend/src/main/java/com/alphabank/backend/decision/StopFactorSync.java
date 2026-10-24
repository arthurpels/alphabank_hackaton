package com.alphabank.backend.decision;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.alphabank.backend.closer.SpotResolutionWriter;
import com.alphabank.backend.enrichment.model.SpotDraft;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.SpotRecord;

@Component
public class StopFactorSync {

    private final SpotRepository spotRepository;
    private final SpotResolutionWriter writer;
    private final Clock clock;

    public StopFactorSync(SpotRepository spotRepository, SpotResolutionWriter writer, Clock clock) {
        this.spotRepository = spotRepository;
        this.writer = writer;
        this.clock = clock;
    }

    public void sync(UUID applicationId, List<SpotDraft> drafts) {
        List<SpotDraft> fresh = drafts.stream().filter(draft -> draft.type() == SpotType.STOP_FACTOR).toList();
        Set<String> freshKeys = fresh.stream().map(StopFactorSync::key).collect(Collectors.toSet());
        List<SpotRecord> active = spotRepository.findByApplicationId(applicationId).stream()
                .filter(spot -> spot.type() == SpotType.STOP_FACTOR && spot.status() != SpotStatus.CLOSED)
                .toList();
        Set<String> activeKeys = active.stream().map(StopFactorSync::key).collect(Collectors.toSet());

        active.stream()
                .filter(spot -> !freshKeys.contains(key(spot)))
                .forEach(spot -> writer.close(spot, null, "Автопроверка: источник больше не подтверждает стоп-фактор"));

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<SpotDraft> added = fresh.stream().filter(draft -> !activeKeys.contains(key(draft))).toList();
        for (int i = 0; i < added.size(); i++) {
            SpotDraft draft = added.get(i);
            writer.create(new SpotRecord(UUID.randomUUID(), applicationId, draft.type(), draft.title(),
                    draft.clientHint(), draft.scoringFactor(), draft.pricePp(), draft.closeMethod(), SpotStatus.OPEN,
                    DecidedBy.SYSTEM, draft.source(), draft.removable(), List.of(), now.plusNanos(i * 1_000L),
                    null), "Автопроверка: новый стоп-фактор");
        }
    }

    private static String key(SpotDraft draft) {
        return draft.source() + "|" + draft.title();
    }

    private static String key(SpotRecord spot) {
        return spot.source() + "|" + spot.title();
    }
}
