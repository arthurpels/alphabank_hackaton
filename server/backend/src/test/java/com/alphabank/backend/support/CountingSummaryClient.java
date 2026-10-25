package com.alphabank.backend.support;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.alphabank.backend.dto.response.LlmMetaDto;
import com.alphabank.backend.dto.response.SummaryItemDto;
import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.scoringclient.SummaryClient;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;

public class CountingSummaryClient implements SummaryClient {

    public static final LlmMetaDto LLM = new LlmMetaDto("qwen3:4b",
            "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7", "summary-v1", false);

    private final AtomicInteger calls = new AtomicInteger();
    private volatile CountDownLatch latch = new CountDownLatch(0);
    private volatile boolean llm;

    @Override
    public UnderwritingSummaryDto summarize(SummaryRequest request) {
        calls.incrementAndGet();
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        if (llm) {
            List<SummaryItemDto> items = java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(new SummaryItemDto("Вывод подготовлен LLM",
                            request.facts().stream().limit(1).toList())),
                    request.spots().stream().map(spot -> new SummaryItemDto("Пятно: " + spot.title(), List.of())))
                    .toList();
            return new UnderwritingSummaryDto("LLM", items, LLM);
        }
        return new UnderwritingSummaryDto("TEMPLATE", request.facts().stream()
                .map(fact -> new SummaryItemDto(fact.label(), List.of(fact)))
                .toList(), null);
    }

    public int calls() {
        return calls.get();
    }

    public void useLlm() {
        llm = true;
    }

    public void pauseNext() {
        latch = new CountDownLatch(1);
    }

    public void resume() {
        latch.countDown();
    }

    public void reset() {
        latch.countDown();
        latch = new CountDownLatch(0);
        calls.set(0);
        llm = false;
    }
}
