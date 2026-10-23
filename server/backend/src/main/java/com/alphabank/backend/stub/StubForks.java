package com.alphabank.backend.stub;

import java.util.List;

import com.alphabank.backend.dto.response.ForkDto;
import com.alphabank.backend.dto.response.ZoneThresholdsDto;
import com.alphabank.backend.util.ForkZoneResolver;

public final class StubForks {

    public static final ZoneThresholdsDto EQUIPMENT_THRESHOLDS = new ZoneThresholdsDto(20, 24);

    private StubForks() {
    }

    public static ForkDto afterEvaluate() {
        return fork("d001", 17, 26, 1);
    }

    public static ForkDto afterOfd() {
        return fork("d002", 18, 23.5, 2);
    }

    public static ForkDto afterAnomalyAnswer() {
        return fork("d003", 18, 21, 3);
    }

    public static ForkDto afterPurpose() {
        return fork("d004", 18, 19.5, 4);
    }

    public static List<ForkDto> history() {
        return List.of(afterEvaluate(), afterOfd(), afterAnomalyAnswer(), afterPurpose());
    }

    private static ForkDto fork(String idSuffix, double from, double to, int minutes) {
        return new ForkDto(StubIds.id(idSuffix), from, to,
                ForkZoneResolver.resolve(from, to, EQUIPMENT_THRESHOLDS), EQUIPMENT_THRESHOLDS,
                StubIds.RULES_VERSION, StubIds.T0.plusMinutes(minutes));
    }
}
