package com.alphabank.backend.util;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.alphabank.backend.dto.response.FieldDiffDto;
import com.fasterxml.jackson.databind.JsonNode;

public final class JsonDiff {

    public static final String ROOT = "$";

    private JsonDiff() {
    }

    public static List<FieldDiffDto> diff(JsonNode expected, JsonNode actual) {
        List<FieldDiffDto> result = new ArrayList<>();
        collect(ROOT, expected, actual, result);
        return result;
    }

    private static void collect(String path, JsonNode expected, JsonNode actual, List<FieldDiffDto> result) {
        if (expected != null && expected.equals(actual)) {
            return;
        }
        if (expected != null && actual != null && expected.isObject() && actual.isObject()) {
            TreeSet<String> names = new TreeSet<>();
            expected.fieldNames().forEachRemaining(names::add);
            actual.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> collect(path + "." + name, expected.get(name), actual.get(name), result));
            return;
        }
        if (expected != null && actual != null && expected.isArray() && actual.isArray()) {
            for (int i = 0; i < Math.max(expected.size(), actual.size()); i++) {
                collect(path + "[" + i + "]", expected.get(i), actual.get(i), result);
            }
            return;
        }
        result.add(new FieldDiffDto(path, expected, actual));
    }
}
