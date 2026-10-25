package com.alphabank.backend.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.client.ResourceAccessException;

import com.alphabank.backend.scoringclient.PurposeClient;
import com.alphabank.backend.scoringclient.dto.LlmMeta;
import com.alphabank.backend.scoringclient.dto.ProfileCheck;
import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;
import com.alphabank.backend.scoringclient.enums.ProfileStatus;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.PurposeStatus;

public class CountingPurposeClient implements PurposeClient {

    public static final LlmMeta LLM = new LlmMeta("qwen3:4b",
            "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7", "bba46b2556f0", false);

    public static final String ACTIVITY = "ОКВЭД 56.30 — Деятельность по предоставлению продуктов питания и напитков";

    private final AtomicInteger calls = new AtomicInteger();
    private volatile PurposeRequest lastRequest;

    @Override
    public PurposeResponse classify(PurposeRequest request) {
        calls.incrementAndGet();
        PurposeResponse response = answer(request);
        if (request.okved() == null || response.profile() != null) {
            return response;
        }
        return new PurposeResponse(response.status(), response.purpose(), response.applicable(),
                response.generatedBy(), response.matchedKeywords(), response.reason(), response.llm(),
                new ProfileCheck(request.okved(), ACTIVITY, ProfileStatus.NOT_CHECKED, null, null, null, null));
    }

    private PurposeResponse answer(PurposeRequest request) {
        lastRequest = request;
        String goal = request.goal() == null ? "" : request.goal().toLowerCase();
        if (goal.contains("трактор")) {
            return new PurposeResponse(PurposeStatus.DECIDED, Purpose.MATCH, true, "KEYWORDS", List.of("трактор"),
                    null, null, new ProfileCheck(request.okved(), ACTIVITY, ProfileStatus.DOES_NOT_FIT,
                    "Трактор не нужен кофейне", "трактор", null, LLM));
        }
        if (goal.contains("кофемашин") || goal.contains("витрин")) {
            return keywords(PurposeStatus.DECIDED, Purpose.MATCH, List.of("кофемашин"));
        }
        if (goal.contains("станок")) {
            return keywords(PurposeStatus.DECIDED, Purpose.MISMATCH, List.of("станок"));
        }
        if (goal.contains("для цеха")) {
            return new PurposeResponse(PurposeStatus.DECIDED, Purpose.MATCH, true, "LLM", List.of(),
                    "Оснащение цеха — покупка оборудования", LLM, null);
        }
        if (goal.contains("недоступн")) {
            return keywords(PurposeStatus.UNAVAILABLE, null, List.of());
        }
        if (goal.contains("таймаут")) {
            throw new ResourceAccessException("Read timed out");
        }
        return new PurposeResponse(PurposeStatus.UNCLEAR, null, true, "LLM", List.of(), "Цель слишком общая", LLM,
                null);
    }

    public PurposeRequest lastRequest() {
        return lastRequest;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
        lastRequest = null;
    }

    private static PurposeResponse keywords(PurposeStatus status, Purpose purpose, List<String> matched) {
        return new PurposeResponse(status, purpose, true, "KEYWORDS", matched, null, null, null);
    }
}
