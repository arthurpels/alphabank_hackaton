package com.alphabank.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.security.DemoUserAuthFilter;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.support.CountingPurposeClient;
import com.alphabank.backend.support.FakeExternalServicesConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeExternalServicesConfig.class)
class PurposeVerdictIntegrationTest {

    private static final String API = "/api/v1/applications";
    private static final String COFFEE = StubIds.COFFEE_USER_ID.toString();
    private static final String COFFEE_REQUEST = """
            {"productCode":"EQUIPMENT","amount":200000000,"termMonths":36,"goal":"витрина",
             "consents":{"bki":true,"personalData":true}}
            """;
    private static final String PURPOSE = "$.spots[?(@.scoringFactor == 'purpose')]";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MocksClient mocksClient;

    @Autowired
    private CountingPurposeClient purposeClient;

    @BeforeEach
    void reset() {
        mocksClient.reset();
        purposeClient.reset();
    }

    @Test
    void llmVerdictIsStoredWithVersionsAndJournaled() throws Exception {
        String id = evaluated();

        answerPurpose(id, "оснащение для цеха")
                .andExpect(status().isOk())
                .andExpect(jsonPath(PURPOSE + ".status").value("CLOSED"))
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.optionCode").value("MATCH"))
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.status").value("DECIDED"))
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.generatedBy").value("LLM"))
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.reason")
                        .value("Оснащение цеха — покупка оборудования"))
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.llm.model").value("qwen3:4b"))
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.llm.promptVersion").value("bba46b2556f0"))
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.productCode").value("EQUIPMENT"));

        String history = history(id);
        List<String> reasons = JsonPath.read(history, "$[?(@.payload.generatedBy == 'LLM')].reason");
        List<String> digests = JsonPath.read(history, "$[?(@.payload.generatedBy == 'LLM')].payload.llm.digest");
        assertThat(reasons).containsExactly("Цель кредита: оснащение для цеха — решила LLM qwen3:4b, промпт bba46b2556f0");
        assertThat(digests).containsExactly(CountingPurposeClient.LLM.digest());
    }

    @Test
    void ruleVerdictIsJournaledAsKeywords() throws Exception {
        String id = evaluated();

        String json = answerPurpose(id, "2 кофемашины и витрина")
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.generatedBy").value("KEYWORDS"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Object> llm = JsonPath.read(json, PURPOSE + ".lastAnswer.purposeVerdict.llm");
        assertThat(llm).containsOnlyNulls();

        List<String> reasons = JsonPath.read(history(id), "$[?(@.payload.generatedBy == 'KEYWORDS')].reason");
        assertThat(reasons).containsExactly("Цель кредита: 2 кофемашины и витрина — решило правило: словарь целей");
    }

    @Test
    void tenRecalculationsNeverCallPurpose() throws Exception {
        String id = evaluated();
        answerPurpose(id, "оснащение для цеха").andExpect(status().isOk());
        assertThat(purposeClient.calls()).isEqualTo(1);

        for (int i = 1; i <= 10; i++) {
            mvc.perform(patch(API + "/" + id).header(DemoUserAuthFilter.HEADER, COFFEE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":" + (200000000 + i * 1000000) + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(PURPOSE + ".lastAnswer.optionCode").value("MATCH"));
        }

        assertThat(purposeClient.calls()).isEqualTo(1);
    }

    @Test
    void sameTextIsNotSentAgainButChangedTextIs() throws Exception {
        String id = evaluated();
        answerPurpose(id, "на развитие бизнеса")
                .andExpect(jsonPath(PURPOSE + ".status").value("OPEN"))
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.verdict").value("REJECTED"))
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.status").value("UNCLEAR"));

        answerPurpose(id, "  На   развитие БИЗНЕСА ")
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.status").value("UNCLEAR"));
        assertThat(purposeClient.calls()).isEqualTo(1);
        List<Boolean> reused = JsonPath.read(history(id), "$[?(@.payload.purposeStatus == 'UNCLEAR')].payload.reused");
        assertThat(reused).containsExactly(false, true);

        answerPurpose(id, "оснащение для цеха").andExpect(jsonPath(PURPOSE + ".status").value("CLOSED"));
        assertThat(purposeClient.calls()).isEqualTo(2);
    }

    @Test
    void unavailableModelIsAskedAgain() throws Exception {
        String id = evaluated();
        answerPurpose(id, "модель недоступна")
                .andExpect(jsonPath(PURPOSE + ".lastAnswer.purposeVerdict.status").value("UNAVAILABLE"));
        answerPurpose(id, "модель недоступна");

        assertThat(purposeClient.calls()).isEqualTo(2);
    }

    @Test
    void verdictForAnotherProductIsNotReused() throws Exception {
        String id = evaluated();
        answerPurpose(id, "на развитие бизнеса");
        mvc.perform(patch(API + "/" + id).header(DemoUserAuthFilter.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"WORKING_CAPITAL\",\"amount\":200000000,\"termMonths\":36}"))
                .andExpect(status().isOk());
        answerPurpose(id, "на развитие бизнеса");

        assertThat(purposeClient.calls()).isEqualTo(2);
    }

    private String evaluated() throws Exception {
        String json = mvc.perform(post(API).header(DemoUserAuthFilter.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content(COFFEE_REQUEST))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.application.id");
        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions answerPurpose(String id, String text) throws Exception {
        String json = mvc.perform(get(API + "/" + id).header(DemoUserAuthFilter.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, PURPOSE + ".id");
        return mvc.perform(post(API + "/" + id + "/spots/" + ids.get(0) + "/close")
                .header(DemoUserAuthFilter.HEADER, COFFEE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"ANSWER\",\"text\":\"" + text + "\"}"));
    }

    private String history(String id) throws Exception {
        return mvc.perform(get(API + "/" + id + "/history").header(DemoUserAuthFilter.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
