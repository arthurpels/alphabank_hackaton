package com.alphabank.backend.stub;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.ForkDto;
import com.alphabank.backend.dto.response.ZoneThresholdsDto;
import com.alphabank.backend.util.ForkZoneResolver;

public final class StubForks {

    public static final ZoneThresholdsDto THRESHOLDS = new ZoneThresholdsDto(21, 24);

    private StubForks() {
    }

    public static ForkDto wide() {
        return fork("d001", 17, 26, 0);
    }

    public static ForkDto middle() {
        return fork("d002", 17.5, 22, 2);
    }

    public static ForkDto narrow() {
        return fork("d003", 18, 19.5, 3);
    }

    public static ForkDto longerTerm() {
        return fork("d005", 19, 20.5, 5);
    }

    public static ForkDto smallerAmount() {
        return fork("d006", 18.5, 20, 5);
    }

    public static List<ForkDto> history() {
        return List.of(wide(), fork("d004", 17.5, 23, 2), narrow());
    }

    private static ForkDto fork(String idSuffix, double from, double to, int minutes) {
        UUID id = StubIds.id(idSuffix);
        return new ForkDto(id, from, to, ForkZoneResolver.resolve(from, to, THRESHOLDS), THRESHOLDS,
                StubIds.RULES_VERSION, StubIds.T0.plusMinutes(minutes));
    }
}
