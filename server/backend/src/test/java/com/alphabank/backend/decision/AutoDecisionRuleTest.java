package com.alphabank.backend.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void leftEdgeInRejectZoneLooksForOtherConditionsFirst() {
        AutoDecision decision = rule.decide(List.of(spot(SpotType.NO_DATA, SpotStatus.OPEN, 3.5, null)),
                score(24.0, 27.0, true));

        assertThat(decision.outcome()).isEqualTo(AutoDecisionOutcome.OFFER_GRID);
        assertThat(decision.reasonSpotId()).isNull();
        assertThat(decision.reason()).isEqualTo("Левый край вилки 24.0 % ≥ порога отказа 24.0 %");
    }

    @Test
    void reasonSpotIsMostExpensiveNotClosedSpot() {
        SpotRecord cheap = spot(SpotType.NO_DATA, SpotStatus.OPEN, 0.5, null);
        SpotRecord expensive = spot(SpotType.NO_DATA, SpotStatus.ON_REVIEW, 3.5, null);
        SpotRecord closed = spot(SpotType.NO_DATA, SpotStatus.CLOSED, 9.0, null);

        assertThat(rule.reasonSpotId(List.of(cheap, expensive, closed))).isEqualTo(expensive.id());
    }

    @Test
    void equalPriceReasonSpotIsTheFirstOne() {
        SpotRecord first = spot(SpotType.NO_DATA, SpotStatus.OPEN, 1.5, null);
        SpotRecord second = spot(SpotType.COLLATERAL, SpotStatus.OPEN, 1.5, null);

        assertThat(rule.reasonSpotId(List.of(first, second))).isEqualTo(first.id());
        assertThat(rule.reasonSpotId(List.of(second, first))).isEqualTo(second.id());
    }

    @Test
    void failedPolicyLooksForOtherConditions() {
        AutoDecision policy = rule.decide(List.of(), score(18.0, 19.5, false));
        AutoDecision leftEdge = rule.decide(List.of(), score(21.0, 23.0, true));

        assertThat(policy.outcome()).isEqualTo(AutoDecisionOutcome.OFFER_GRID);
        assertThat(policy.reason()).isEqualTo("Политика продукта не выполнена");
        assertThat(leftEdge.outcome()).isEqualTo(AutoDecisionOutcome.OFFER_GRID);
        assertThat(leftEdge.reason()).isEqualTo("Левый край вилки 21.0 % > порога одобрения 20.0 %");
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
    void applicationWithoutSpotsCannotBeRejectedWithoutReason() {
        assertThatThrownBy(() -> rule.reasonSpotId(List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allClosedReasonSpotIsDebtLoadThenFirstSpot() {
        SpotRecord collateral = spot(SpotType.COLLATERAL, SpotStatus.CLOSED, 1.5, null);
        SpotRecord debtLoad = new SpotRecord(UUID.randomUUID(), UUID.randomUUID(), SpotType.NO_DATA, "пятно", null,
                "debt_load", 3.5, null, SpotStatus.CLOSED, DecidedBy.SYSTEM, null, null, List.of(),
                OffsetDateTime.now(), null);

        assertThat(rule.reasonSpotId(List.of(collateral, debtLoad))).isEqualTo(debtLoad.id());
        assertThat(rule.reasonSpotId(List.of(collateral))).isEqualTo(collateral.id());
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
