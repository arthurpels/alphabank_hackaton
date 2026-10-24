package com.alphabank.backend.model;

import com.alphabank.backend.scoringclient.enums.Collateral;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.Stability;

public record FactorStates(Stability stability, Purpose purpose, Collateral collateral) {
}
