package com.alphabank.backend.util;

import java.util.Locale;
import java.util.Objects;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        return String.join(" ", Objects.requireNonNullElse(text, "").toLowerCase(Locale.ROOT).replace('ё', 'е')
                .trim().split("\\s+"));
    }
}
