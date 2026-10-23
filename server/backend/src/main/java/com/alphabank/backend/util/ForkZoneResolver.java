package com.alphabank.backend.util;

import com.alphabank.backend.dto.response.ZoneThresholdsDto;
import com.alphabank.backend.enums.DecisionZone;

public final class ForkZoneResolver {

    private ForkZoneResolver() {
    }

    public static DecisionZone resolve(double rateFrom, double rateTo, ZoneThresholdsDto thresholds) {
        if (rateTo <= thresholds.approveMax()) {
            return DecisionZone.APPROVE;
        }
        if (rateFrom >= thresholds.rejectMin()) {
            return DecisionZone.REJECT;
        }
        return DecisionZone.UNDERWRITER;
    }
}
