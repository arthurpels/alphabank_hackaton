package com.alphabank.backend.closer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import com.alphabank.backend.dto.response.LlmMetaDto;
import com.alphabank.backend.dto.response.ProfileCheckDto;
import com.alphabank.backend.dto.response.PurposeVerdictDto;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.model.ResolvedPurpose;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.PurposeClient;
import com.alphabank.backend.scoringclient.dto.LlmMeta;
import com.alphabank.backend.scoringclient.dto.ProfileCheck;
import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;
import com.alphabank.backend.scoringclient.enums.PurposeStatus;
import com.alphabank.backend.util.TextNormalizer;

@Component
public class PurposeVerdictResolver {

    private static final Logger log = LoggerFactory.getLogger(PurposeVerdictResolver.class);

    private final PurposeClient purposeClient;
    private final SpotAnswerRepository spotAnswerRepository;

    public PurposeVerdictResolver(PurposeClient purposeClient, SpotAnswerRepository spotAnswerRepository) {
        this.purposeClient = purposeClient;
        this.spotAnswerRepository = spotAnswerRepository;
    }

    public ResolvedPurpose resolve(SpotRecord spot, ProductCode product, String goal, String okved) {
        return stored(spot, product, goal, okved)
                .map(verdict -> new ResolvedPurpose(verdict, true))
                .orElseGet(() -> new ResolvedPurpose(classify(product, goal, okved), false));
    }

    private PurposeVerdictDto classify(ProductCode product, String goal, String okved) {
        try {
            return toVerdict(product, purposeClient.classify(new PurposeRequest(product, goal, okved)));
        } catch (RestClientException e) {
            log.warn("ML недоступна при разборе цели: {}", e.getMessage());
            return toVerdict(product, new PurposeResponse(PurposeStatus.UNAVAILABLE, null, true, "KEYWORDS",
                    List.of(), "ML временно недоступна", null, null));
        }
    }

    private Optional<PurposeVerdictDto> stored(SpotRecord spot, ProductCode product, String goal, String okved) {
        String normalized = TextNormalizer.normalize(goal);
        List<SpotAnswerRecord> answers = spotAnswerRepository.findBySpotId(spot.id());
        return answers.stream()
                .filter(answer -> answer.purposeVerdict() != null)
                .filter(answer -> answer.purposeVerdict().productCode() == product)
                .filter(answer -> answer.purposeVerdict().status() != PurposeStatus.UNAVAILABLE)
                .filter(answer -> Objects.equals(okved(answer.purposeVerdict()), okved))
                .filter(answer -> TextNormalizer.normalize(answer.text()).equals(normalized))
                .map(SpotAnswerRecord::purposeVerdict)
                .reduce((first, second) -> second);
    }

    private static String okved(PurposeVerdictDto verdict) {
        return verdict.profile() == null ? null : verdict.profile().okved();
    }

    private static PurposeVerdictDto toVerdict(ProductCode product, PurposeResponse response) {
        return new PurposeVerdictDto(product, status(response), response.purpose(), response.generatedBy(),
                response.matchedKeywords() == null ? List.of() : response.matchedKeywords(), response.reason(),
                toDto(response.llm()), toDto(response.profile()));
    }

    private static ProfileCheckDto toDto(ProfileCheck profile) {
        return profile == null ? null : new ProfileCheckDto(profile.okved(), profile.activity(), profile.status(),
                profile.reason(), profile.evidence(), profile.violation(), toDto(profile.llm()));
    }

    private static LlmMetaDto toDto(LlmMeta llm) {
        return llm == null ? null : new LlmMetaDto(llm.model(), llm.digest(), llm.promptVersion(), llm.cached());
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
}
