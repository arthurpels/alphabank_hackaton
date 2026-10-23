package com.alphabank.backend.stub;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public final class StubIds {

    public static final String RULES_VERSION = "2026-10-23.draft1";
    public static final OffsetDateTime T0 = OffsetDateTime.of(2026, 10, 23, 10, 0, 0, 0, ZoneOffset.ofHours(3));

    public static final UUID COFFEE_USER_ID = id("101");
    public static final UUID BUILDER_USER_ID = id("102");
    public static final UUID SELLER_USER_ID = id("103");
    public static final UUID TAX_DEBT_USER_ID = id("104");
    public static final UUID SHELL_USER_ID = id("105");
    public static final UUID UNDERWRITER_ID = id("901");

    public static final UUID COFFEE_COMPANY_ID = id("201");
    public static final UUID COFFEE_APPLICATION_ID = id("a001");

    public static final UUID SPOT_OFD = id("b001");
    public static final UUID SPOT_STABILITY = id("b002");
    public static final UUID SPOT_GOAL = id("b003");
    public static final UUID SPOT_COLLATERAL = id("b004");
    public static final UUID SPOT_MARCH = id("b005");
    public static final UUID SPOT_UNDERWRITER = id("b006");

    public static final UUID OFFER_REQUESTED = id("c001");
    public static final UUID OFFER_MINUS_20 = id("c002");
    public static final UUID OFFER_MINUS_35 = id("c003");

    private StubIds() {
    }

    public static UUID id(String suffix) {
        String hex = "000000000000" + suffix;
        return UUID.fromString("00000000-0000-0000-0000-" + hex.substring(hex.length() - 12));
    }
}
