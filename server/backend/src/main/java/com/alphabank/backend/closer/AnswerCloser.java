package com.alphabank.backend.closer;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.request.SpotCloseRequest;
import com.alphabank.backend.dto.response.AnomalyVerdictDto;
import com.alphabank.backend.dto.response.AnswerCheckDto;
import com.alphabank.backend.dto.response.AnswerClassificationDto;
import com.alphabank.backend.dto.response.PurposeVerdictDto;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.model.ResolvedAnomalyAnswer;
import com.alphabank.backend.model.ResolvedPurpose;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.enums.AnswerCheckVerdict;
import com.alphabank.backend.scoringclient.enums.AnswerClassifyStatus;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.scoringclient.enums.ProfileStatus;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.PurposeStatus;
import com.alphabank.backend.service.impl.FactorStateServiceImpl;

@Component
public class AnswerCloser implements SpotCloser {

    private static final String UNCLEAR_GOAL = "Не удалось понять цель — напишите подробнее, что именно покупаете";

    private final PurposeVerdictResolver purposeVerdictResolver;
    private final AnomalyAnswerResolver anomalyAnswerResolver;
    private final EnrichmentService enrichmentService;
    private final SpotResolutionWriter writer;

    public AnswerCloser(PurposeVerdictResolver purposeVerdictResolver, AnomalyAnswerResolver anomalyAnswerResolver,
                        EnrichmentService enrichmentService, SpotResolutionWriter writer) {
        this.purposeVerdictResolver = purposeVerdictResolver;
        this.anomalyAnswerResolver = anomalyAnswerResolver;
        this.enrichmentService = enrichmentService;
        this.writer = writer;
    }

    @Override
    public CloseMethod method() {
        return CloseMethod.ANSWER;
    }

    @Override
    public void close(SpotCloseContext context) {
        SpotRecord spot = context.spot();
        SpotCloseRequest request = context.request();
        if (FactorStateServiceImpl.PURPOSE.equals(spot.scoringFactor())) {
            closePurpose(context);
        } else if (spot.type() == SpotType.ANOMALY
                && FactorStateServiceImpl.REVENUE_STABILITY.equals(spot.scoringFactor())) {
            closeAnomaly(context);
        } else {
            writer.sendToReview(spot, context.user().id(), request.optionCode(), request.text(),
                    request.documentId());
        }
    }

    private void closePurpose(SpotCloseContext context) {
        SpotCloseRequest request = context.request();
        String goal = request.text() != null ? request.text() : request.optionCode();
        ResolvedPurpose resolved = purposeVerdictResolver.resolve(context.spot(), context.product().code(), goal,
                enrichmentService.okvedMain(context.inn()).orElse(null));
        PurposeVerdictDto verdict = resolved.verdict();
        String source = source(resolved);
        if (verdict.status() == PurposeStatus.NOT_APPLICABLE) {
            writer.acceptPurpose(context.spot(), context.user().id(), null, goal, verdict, resolved.reused(),
                    "Цель для этого продукта не учитывается — " + source);
        } else if (verdict.status() == PurposeStatus.UNAVAILABLE) {
            writer.sendPurposeToReview(context.spot(), context.user().id(), null, goal, verdict, resolved.reused(),
                    "LLM недоступна — ответ передан на проверку специалисту");
        } else if (verdict.purpose() == null) {
            writer.rejectPurpose(context.spot(), context.user().id(), goal, verdict, resolved.reused(),
                    UNCLEAR_GOAL, UNCLEAR_GOAL + " — " + source);
        } else if (verdict.purpose() == Purpose.MATCH && verdict.profile() != null
                && verdict.profile().status() == ProfileStatus.DOES_NOT_FIT) {
            writer.sendPurposeToReview(context.spot(), context.user().id(), verdict.purpose().name(), goal, verdict,
                    resolved.reused(), "Цель не похожа на деятельность компании (" + verdict.profile().activity()
                            + "): " + verdict.profile().evidence() + " — ответ передан на проверку специалисту — "
                            + source);
        } else {
            writer.acceptPurpose(context.spot(), context.user().id(), verdict.purpose().name(), goal, verdict,
                    resolved.reused(), "Цель кредита: " + goal + " — " + source);
        }
    }

    private static String source(ResolvedPurpose resolved) {
        PurposeVerdictDto verdict = resolved.verdict();
        String decided = verdict.llm() != null
                ? "решила LLM " + verdict.llm().model() + ", промпт " + verdict.llm().promptVersion()
                : verdict.status() == PurposeStatus.UNAVAILABLE ? "LLM недоступна" : "решило правило: словарь целей";
        return resolved.reused() ? decided + "; вердикт сохранён ранее, модель не вызывалась" : decided;
    }

    private void closeAnomaly(SpotCloseContext context) {
        SpotRecord spot = context.spot();
        SpotCloseRequest request = context.request();
        ResolvedAnomalyAnswer resolved = anomalyAnswerResolver.resolve(spot, request.optionCode(), request.text(),
                context.inn());
        AnomalyVerdictDto verdict = resolved.verdict();
        String optionCode = verdict.code() == null ? null : verdict.code().name();
        AnswerCheckDto check = verdict.check();
        if (check != null && check.verdict() == AnswerCheckVerdict.ACCEPTED) {
            writer.acceptAnomalyAnswer(spot, context.user().id(), optionCode, request.text(), verdict,
                    resolved.reused(), "Ответ подтверждён данными (правило " + check.rule() + "): "
                            + check.explanation() + " — " + anomalySource(resolved));
        } else {
            writer.sendAnomalyAnswerToReview(spot, context.user().id(), optionCode, request.text(), verdict,
                    resolved.reused(), reviewReason(resolved));
        }
    }

    private static String reviewReason(ResolvedAnomalyAnswer resolved) {
        AnomalyVerdictDto verdict = resolved.verdict();
        if (verdict.classification() != null
                && verdict.classification().status() == AnswerClassifyStatus.UNAVAILABLE) {
            return "LLM недоступна — ответ передан на проверку специалисту";
        }
        if (verdict.anomaly() == null) {
            return "Аномалия не найдена в текущих данных источника — ответ передан на проверку специалисту — "
                    + anomalySource(resolved);
        }
        if (verdict.check() == null) {
            return "Проверка данными недоступна — ответ передан на проверку специалисту — " + anomalySource(resolved);
        }
        return verdict.check().explanation() + " — " + anomalySource(resolved);
    }

    private static String anomalySource(ResolvedAnomalyAnswer resolved) {
        AnswerClassificationDto classification = resolved.verdict().classification();
        if (classification == null) {
            return "вариант выбран клиентом";
        }
        String decided = classification.llm() != null
                ? "причину определила LLM " + classification.llm().model() + ", промпт "
                        + classification.llm().promptVersion()
                : classification.status() == AnswerClassifyStatus.UNAVAILABLE ? "LLM недоступна"
                        : "причину определило правило: словарь причин";
        return resolved.reused() ? decided + "; вердикт сохранён ранее, модель не вызывалась" : decided;
    }
}
