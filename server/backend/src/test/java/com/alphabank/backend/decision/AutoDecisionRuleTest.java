package com.alphabank.backend.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.dto.Policy;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.scoringclient.dto.Thresholds;

class AutoDecisionRuleTest {

    private final AutoDecisionRule rule = new AutoDecisionRule();

    @Test
    void rightEdgeInApproveZoneApproves() {
        assertThat(rule.decide(List.of(), score(18.0, 19.5, true)).outcome()).isEqualTo(AutoDecisionOutcome.APPROVE);
        assertThat(rule.decide(List.of(), score(18.0, 20.0, true)).outcome()).isEqualTo(AutoDecisionOutcome.APPROVE);
    }

    @Test
    void forkCrossingApproveThresholdGoesToUnderwriting() {
        assertThat(rule.decide(List.of(), score(17.0, 26.0, true)).outcome())
                .isEqualTo(AutoDecisionOutcome.UNDERWRITING);
    }

    @Test
    void leftEdgeInRejectZoneRejectsWithMostExpensiveOpenSpot() {
        SpotRecord cheap = spot(SpotType.NO_DATA, SpotStatus.OPEN, 0.5, null);
        SpotRecord expensive = spot(SpotType.NO_DATA, SpotStatus.OPEN, 3.5, null);
        SpotRecord closed = spot(SpotType.NO_DATA, SpotStatus.CLOSED, 9.0, null);

        AutoDecision decision = rule.decide(List.of(cheap, expensive, closed), score(24.0, 27.0, true));

        assertThat(decision.outcome()).isEqualTo(AutoDecisionOutcome.REJECT);
        assertThat(decision.reasonSpotId()).isEqualTo(expensive.id());
    }

    @Test
    void failedPolicyLooksForOtherConditions() {
        assertThat(rule.decide(List.of(), score(18.0, 19.5, false)).outcome())
                .isEqualTo(AutoDecisionOutcome.OFFER_GRID);
        assertThat(rule.decide(List.of(), score(21.0, 23.0, true)).outcome())
                .isEqualTo(AutoDecisionOutcome.OFFER_GRID);
    }

    @Test
    void openStopFactorRejectsEvenInApproveZone() {
        SpotRecord taxDebt = spot(SpotType.STOP_FACTOR, SpotStatus.OPEN, null, true);
        SpotRecord liquidation = spot(SpotType.STOP_FACTOR, SpotStatus.OPEN, null, false);

        AutoDecision decision = rule.decide(List.of(taxDebt, liquidation), score(18.0, 19.5, true));

        assertThat(decision.outcome()).isEqualTo(AutoDecisionOutcome.REJECT);
        assertThat(decision.reasonSpotId()).isEqualTo(liquidation.id());
    }

    @Test
    void closedStopFactorDoesNotBlock() {
        SpotRecord paid = spot(SpotType.STOP_FACTOR, SpotStatus.CLOSED, null, true);

        assertThat(rule.decide(List.of(paid), score(18.0, 19.5, true)).outcome())
                .isEqualTo(AutoDecisionOutcome.APPROVE);
    }

    @Test
    void rejectZoneWithoutAnySpotGoesToUnderwriter() {
        AutoDecision decision = rule.decide(List.of(), score(24.0, 27.0, true));

        assertThat(decision.outcome()).isEqualTo(AutoDecisionOutcome.UNDERWRITING);
        assertThat(decision.reasonSpotId()).isNull();
    }

    @Test
    void rejectZoneFallsBackToAnySpotAsReason() {
        SpotRecord closed = spot(SpotType.COLLATERAL, SpotStatus.CLOSED, null, null);

        AutoDecision decision = rule.decide(List.of(closed), score(24.0, 27.0, true));

        assertThat(decision.outcome()).isEqualTo(AutoDecisionOutcome.REJECT);
        assertThat(decision.reasonSpotId()).isEqualTo(closed.id());
    }

    private static ScoreResponse score(double from, double to, boolean policyOk) {
        return new ScoreResponse("test", ProductCode.EQUIPMENT, from, to, new Thresholds(20.0, 24.0), List.of(),
                List.of(), new Policy(null, null, null, true, policyOk), to <= 20.0);
    }

    private static SpotRecord spot(SpotType type, SpotStatus status, Double price, Boolean removable) {
        return new SpotRecord(UUID.randomUUID(), UUID.randomUUID(), type, "пятно", null, null, price, null, status,
                DecidedBy.SYSTEM, null, removable, List.of(), OffsetDateTime.now(), null);
    }
}
