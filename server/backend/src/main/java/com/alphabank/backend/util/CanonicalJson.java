package com.alphabank.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class CanonicalJson {

    private final ObjectMapper objectMapper;

    public CanonicalJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(sorted(objectMapper.valueToTree(value)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось записать канонический JSON", e);
        }
    }

    public JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректный JSON в снимке", e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректный JSON в снимке", e);
        }
    }

    public String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    private JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, sorted(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(sorted(item)));
            return result;
        }
        return node;
    }
}
