package com.alphabank.backend.enrichment.model;

import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotType;

public record SpotDraft(
        SpotType type,
        String title,
        String clientHint,
        String scoringFactor,
        Double pricePp,
        CloseMethod closeMethod,
        SourceCode source,
        Boolean removable) {

    public SpotDraft(SpotType type, String title, String clientHint, String scoringFactor,
                     Double pricePp, CloseMethod closeMethod, SourceCode source) {
        this(type, title, clientHint, scoringFactor, pricePp, closeMethod, source, null);
    }
}
