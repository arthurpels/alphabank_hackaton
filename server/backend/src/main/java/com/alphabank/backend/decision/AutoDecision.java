package com.alphabank.backend.decision;

import java.util.UUID;

public record AutoDecision(AutoDecisionOutcome outcome, UUID reasonSpotId, String reason) {
}
