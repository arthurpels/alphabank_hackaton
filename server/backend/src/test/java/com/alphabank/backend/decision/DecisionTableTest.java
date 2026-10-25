package com.alphabank.backend.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.DecisionType;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.dto.Policy;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.scoringclient.dto.Thresholds;
import com.alphabank.backend.service.ForkCalculationService;
import com.alphabank.backend.service.StatusTransitionService;
import com.alphabank.backend.service.SummaryGenerationService;
import com.alphabank.backend.service.impl.AutoCheckServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class DecisionTableTest {

    private static final Path TABLE = Path.of("docs", "decision-table.json");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-23T10:00:00Z");

    static Stream<Arguments> rows() throws IOException {
        JsonNode table = new ObjectMapper().readTree(Files.readString(locate(), StandardCharsets.UTF_8));
        List<Arguments> rows = new ArrayList<>();
        table.get("rows").forEach(row -> rows.add(Arguments.of(row.get("id").asText() + " " + row.get("why").asText(),
                row)));
        return rows.stream();
    }

    @Test
    void tableCoversAllRulesAndBoundaries() throws IOException {
        JsonNode rows = new ObjectMapper().readTree(Files.readString(locate(), StandardCharsets.UTF_8)).get("rows");
        List<String> rules = new ArrayList<>();
        rows.forEach(row -> rules.add(row.get("rule").asText()));

        assertThat(rows.size()).isGreaterThanOrEqualTo(30);
        assertThat(rules).contains("R1", "R2", "R3", "R4", "R5", "R6");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void autoCheckFollowsDecisionTable(String name, JsonNode row) {
        JsonNode input = row.get("input");
        JsonNode expected = row.get("expected");
        UUID applicationId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        List<SpotRecord> spots = spots(applicationId, input.get("spots"));
        ProductCode productCode = ProductCode.valueOf(input.get("productCode").asText());
        ApplicationRecord application = new ApplicationRecord(applicationId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 100_000_000L, 12, "цель", ApplicationStatus.AUTO_CHECK, LocalDate.of(2026, 10, 23),
                NOW, NOW, null);
        ProductRecord product = mock(ProductRecord.class);
        ScoreRequest request = new ScoreRequest(LocalDate.of(2026, 10, 23), productCode, 100_000_000L, 12, "ACTIVE",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1), false, 700, 0, 0, 0L, 0, null, null, null, null);
        ScoreResponse score = new ScoreResponse("2026-10-23.draft1", productCode, input.get("rateFrom").asDouble(),
                input.get("rateTo").asDouble(),
                new Thresholds(input.get("approveMax").asDouble(), input.get("rejectMin").asDouble()), List.of(),
                List.of(), new Policy(null, null, null, true, input.get("policyOk").asBoolean()),
                input.get("policyOk").asBoolean() && input.get("rateTo").asDouble() <= input.get("approveMax")
                        .asDouble());
        ForkSnapshotRecord snapshot = new ForkSnapshotRecord(UUID.randomUUID(), applicationId, score.rateFrom(),
                score.rateTo(), score.thresholds().approveMax(), score.thresholds().rejectMin(), score.rulesVersion(),
                request.asOf(), NOW, null);

        ForkCalculationService forkCalculationService = mock(ForkCalculationService.class);
        SpotRepository spotRepository = mock(SpotRepository.class);
        GridOfferWriter gridOfferWriter = mock(GridOfferWriter.class);
        OfferWriter offerWriter = mock(OfferWriter.class);
        DecisionWriter decisionWriter = mock(DecisionWriter.class);
        StatusTransitionService statusTransitionService = mock(StatusTransitionService.class);
        EnrichmentResult enrichment = new EnrichmentResult("7701234560", null, null, null, List.of(),
                request.asOf(), List.of());
        when(forkCalculationService.calculate(application, product, "7701234560"))
                .thenReturn(new ForkCalculation(enrichment, request, score));
        when(forkCalculationService.save(applicationId, request, score)).thenReturn(snapshot);
        when(spotRepository.findByApplicationId(applicationId)).thenReturn(spots);
        when(gridOfferWriter.save(eq(applicationId), eq(request), any())).thenReturn(input.get("gridOffers").asInt());
        when(statusTransitionService.apply(eq(applicationId), any(), eq(TransitionInitiator.SYSTEM), isNull(),
                anyString())).thenAnswer(call -> call.getArgument(1));
        AutoCheckServiceImpl autoCheck = new AutoCheckServiceImpl(forkCalculationService,
                mock(EnrichmentService.class), mock(StopFactorSync.class), new AutoDecisionRule(), gridOfferWriter,
                spotRepository, offerWriter, decisionWriter, statusTransitionService,
                mock(SummaryGenerationService.class),
                Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));

        ApplicationStatus status = autoCheck.run(application, product, "7701234560");

        assertThat(status).isEqualTo(ApplicationStatus.valueOf(expected.get("status").asText()));
        if (expected.get("decisionType").isNull()) {
            verify(decisionWriter, never()).bySystem(any(), any(), any(), any(), any());
        } else {
            UUID reasonSpotId = expected.get("reasonSpotId").isNull() ? null
                    : spotId(applicationId, expected.get("reasonSpotId").asText());
            verify(decisionWriter).bySystem(eq(applicationId),
                    eq(DecisionType.valueOf(expected.get("decisionType").asText())), eq(snapshot), eq(reasonSpotId),
                    anyString());
        }
        verifyOffers(row.get("rule").asText(), expected.get("offers"), applicationId, request, gridOfferWriter,
                offerWriter);
    }

    private static void verifyOffers(String rule, JsonNode offers, UUID applicationId, ScoreRequest request,
                                     GridOfferWriter gridOfferWriter, OfferWriter offerWriter) {
        if (offers.get("requested").asBoolean()) {
            verify(offerWriter).save(eq(applicationId), any(), anyLong(), anyInt(), isNull(), eq(request), any(),
                    eq(OfferStatus.OFFERED), any());
        } else {
            verify(offerWriter, never()).save(any(), any(), anyLong(), anyInt(), any(), any(), any(),
                    any(OfferStatus.class), any());
        }
        boolean gridOffered = rule.equals("R4") || rule.equals("R5");
        verify(gridOfferWriter, gridOffered ? times(1) : never())
                .save(applicationId, request, OfferStatus.OFFERED);
        verify(gridOfferWriter, rule.equals("R6") ? times(1) : never())
                .save(applicationId, request, OfferStatus.CALCULATED);
        if (!offers.get("status").isNull()) {
            assertThat(OfferStatus.valueOf(offers.get("status").asText()))
                    .isEqualTo(rule.equals("R6") ? OfferStatus.CALCULATED : OfferStatus.OFFERED);
        }
    }

    private static List<SpotRecord> spots(UUID applicationId, JsonNode spots) {
        List<SpotRecord> result = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            JsonNode spot = spots.get(i);
            result.add(new SpotRecord(spotId(applicationId, spot.get("id").asText()), applicationId,
                    SpotType.valueOf(spot.get("type").asText()), spot.get("id").asText(), null,
                    spot.get("scoringFactor").isNull() ? null : spot.get("scoringFactor").asText(),
                    spot.get("pricePp").isNull() ? null : spot.get("pricePp").asDouble(), null,
                    SpotStatus.valueOf(spot.get("status").asText()), DecidedBy.SYSTEM, null,
                    spot.get("removable").isNull() ? null : spot.get("removable").asBoolean(), List.of(),
                    NOW.plusSeconds(i), null));
        }
        return result;
    }

    private static UUID spotId(UUID applicationId, String id) {
        return UUID.nameUUIDFromBytes((applicationId + "/" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static Path locate() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve(TABLE))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Не найден " + TABLE + " выше " + Path.of("").toAbsolutePath());
        }
        return dir.resolve(TABLE);
    }
}
