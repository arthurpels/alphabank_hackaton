package com.alphabank.backend.scoringclient.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FactorCode {
    @JsonProperty("credit_history")
    CREDIT_HISTORY,
    @JsonProperty("business_age")
    BUSINESS_AGE,
    @JsonProperty("tax_discipline")
    TAX_DISCIPLINE,
    @JsonProperty("debt_load")
    DEBT_LOAD,
    @JsonProperty("revenue_stability")
    REVENUE_STABILITY,
    @JsonProperty("purpose")
    PURPOSE,
    @JsonProperty("collateral")
    COLLATERAL
}
