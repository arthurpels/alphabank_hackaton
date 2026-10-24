package com.alphabank.backend.stub;

import java.util.UUID;

public final class StubIds {

    public static final UUID COFFEE_USER_ID = id("101");
    public static final UUID BUILDER_USER_ID = id("102");
    public static final UUID SELLER_USER_ID = id("103");
    public static final UUID TAX_DEBT_USER_ID = id("104");
    public static final UUID SHELL_USER_ID = id("105");
    public static final UUID UNDERWRITER_ID = id("901");

    public static final UUID COFFEE_COMPANY_ID = id("201");
    public static final UUID COFFEE_APPLICATION_ID = id("a001");

    private StubIds() {
    }

    public static UUID id(String suffix) {
        String hex = "000000000000" + suffix;
        return UUID.fromString("00000000-0000-0000-0000-" + hex.substring(hex.length() - 12));
    }
}
