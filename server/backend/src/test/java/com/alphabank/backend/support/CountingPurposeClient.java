package com.alphabank.backend.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.alphabank.backend.scoringclient.PurposeClient;
import com.alphabank.backend.scoringclient.dto.LlmMeta;
import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.PurposeStatus;

public class CountingPurposeClient implements PurposeClient {

    public static final LlmMeta LLM = new LlmMeta("qwen3:4b",
            "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7", "bba46b2556f0", false);

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public PurposeResponse classify(PurposeRequest request) {
        calls.incrementAndGet();
        String goal = request.goal() == null ? "" : request.goal().toLowerCase();
        if (goal.contains("кофемашин") || goal.contains("витрин")) {
            return keywords(PurposeStatus.DECIDED, Purpose.MATCH, List.of("кофемашин"));
        }
        if (goal.contains("станок")) {
            return keywords(PurposeStatus.DECIDED, Purpose.MISMATCH, List.of("станок"));
        }
        if (goal.contains("для цеха")) {
            return new PurposeResponse(PurposeStatus.DECIDED, Purpose.MATCH, true, "LLM", List.of(),
                    "Оснащение цеха — покупка оборудования", LLM);
        }
        if (goal.contains("недоступн")) {
            return keywords(PurposeStatus.UNAVAILABLE, null, List.of());
        }
        return new PurposeResponse(PurposeStatus.UNCLEAR, null, true, "LLM", List.of(), "Цель слишком общая", LLM);
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
    }

    private static PurposeResponse keywords(PurposeStatus status, Purpose purpose, List<String> matched) {
        return new PurposeResponse(status, purpose, true, "KEYWORDS", matched, null, null);
    }
}
