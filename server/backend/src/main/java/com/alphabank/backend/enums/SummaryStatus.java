package com.alphabank.backend.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SummaryStatus {
    READY("ready"), GENERATING("generating");

    private final String value;

    SummaryStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
