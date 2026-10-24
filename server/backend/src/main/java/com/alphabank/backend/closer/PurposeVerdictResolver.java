package com.alphabank.backend.closer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.response.LlmMetaDto;
import com.alphabank.backend.dto.response.PurposeVerdictDto;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.model.ResolvedPurpose;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.PurposeClient;
import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;
import com.alphabank.backend.scoringclient.enums.PurposeStatus;

@Component
public class PurposeVerdictResolver {

    private final PurposeClient purposeClient;
    private final SpotAnswerRepository spotAnswerRepository;

    public PurposeVerdictResolver(PurposeClient purposeClient, SpotAnswerRepository spotAnswerRepository) {
        this.purposeClient = purposeClient;
        this.spotAnswerRepository = spotAnswerRepository;
    }

    public ResolvedPurpose resolve(SpotRecord spot, ProductCode product, String goal) {
        return stored(spot, product, goal)
                .map(verdict -> new ResolvedPurpose(verdict, true))
                .orElseGet(() -> new ResolvedPurpose(
                        toVerdict(product, purposeClient.classify(new PurposeRequest(product, goal))), false));
    }

    private Optional<PurposeVerdictDto> stored(SpotRecord spot, ProductCode product, String goal) {
        String normalized = normalize(goal);
        List<SpotAnswerRecord> answers = spotAnswerRepository.findBySpotId(spot.id());
        return answers.stream()
                .filter(answer -> answer.purposeVerdict() != null)
                .filter(answer -> answer.purposeVerdict().productCode() == product)
                .filter(answer -> answer.purposeVerdict().status() != PurposeStatus.UNAVAILABLE)
                .filter(answer -> normalize(answer.text()).equals(normalized))
                .map(SpotAnswerRecord::purposeVerdict)
                .reduce((first, second) -> second);
    }

    private static PurposeVerdictDto toVerdict(ProductCode product, PurposeResponse response) {
        LlmMetaDto llm = response.llm() == null ? null : new LlmMetaDto(response.llm().model(),
                response.llm().digest(), response.llm().promptVersion(), response.llm().cached());
        return new PurposeVerdictDto(product, status(response), response.purpose(), response.generatedBy(),
                response.matchedKeywords() == null ? List.of() : response.matchedKeywords(), response.reason(), llm);
    }

    private static PurposeStatus status(PurposeResponse response) {
        if (response.status() != null) {
            return response.status();
        }
        if (!response.applicable()) {
            return PurposeStatus.NOT_APPLICABLE;
        }
        return response.purpose() == null ? PurposeStatus.UNCLEAR : PurposeStatus.DECIDED;
    }

    private static String normalize(String text) {
        return String.join(" ", Objects.requireNonNullElse(text, "").toLowerCase(Locale.ROOT).replace('ё', 'е')
                .trim().split("\\s+"));
    }
}
