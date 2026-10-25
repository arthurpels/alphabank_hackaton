package com.alphabank.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.security.DemoUserAuthFilter;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.support.CountingPurposeClient;
import com.alphabank.backend.support.FakeExternalServicesConfig;
import com.alphabank.backend.util.CanonicalJson;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeExternalServicesConfig.class)
class ForkVerificationIntegrationTest {

    private static final String API = "/api/v1/applications";
    private static final String COFFEE = StubIds.COFFEE_USER_ID.toString();
    private static final String TECHSNAB = StubIds.TAX_DEBT_USER_ID.toString();
    private static final String UNDERWRITER = StubIds.UNDERWRITER_ID.toString();
    private static final String COFFEE_REQUEST = """
            {"productCode":"EQUIPMENT","amount":200000000,"termMonths":36,"goal":"витрина",
             "consents":{"bki":true,"personalData":true}}
            """;
    private static final String TECHSNAB_REQUEST = """
            {"productCode":"WORKING_CAPITAL","amount":80000000,"termMonths":12,"goal":"запчасти",
             "consents":{"bki":true,"personalData":true}}
            """;
    private static final String PURPOSE = "$.spots[?(@.scoringFactor == 'purpose')]";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MocksClient mocksClient;

    @Autowired
    private CountingPurposeClient purposeClient;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CanonicalJson canonicalJson;

    @BeforeEach
    void reset() {
        mocksClient.reset();
        purposeClient.reset();
    }

    @Test
    void freshSnapshotIsReproducible() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String snapshotId = latestSnapshotId(COFFEE, id);

        verify(COFFEE, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").value(snapshotId))
                .andExpect(jsonPath("$.reproducible").value(true))
                .andExpect(jsonPath("$.diff").isEmpty())
                .andExpect(jsonPath("$.rulesVersion").value("2026-10-23.draft1"))
                .andExpect(jsonPath("$.engineVersion").value(FakeExternalServicesConfig.ENGINE_VERSION))
                .andExpect(jsonPath("$.currentEngineVersion").value(FakeExternalServicesConfig.ENGINE_VERSION));

        mvc.perform(get(API + "/" + id).header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fork.reproducible").value(true));

        String request = column("score_request", snapshotId);
        assertThat(column("request_hash", snapshotId)).isEqualTo(canonicalJson.sha256(request)).hasSize(64);
        assertThat(request).startsWith("{\"amount\":200000000,\"asOf\":\"2026-10-23\",\"bkiCurrentOverdue\":0");
    }

    @Test
    void verifyIsStableAndAvailableToUnderwriter() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String first = body(verify(COFFEE, id));
        String second = body(verify(UNDERWRITER, id));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void changedResponseIsReportedAsDiff() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String snapshotId = latestSnapshotId(COFFEE, id);
        String stored = column("score_response", snapshotId);
        Double rateTo = JsonPath.read(stored, "$.rateTo");
        updateColumn("score_response", snapshotId, stored.replace("\"rateTo\":" + rateTo, "\"rateTo\":99.0"));

        verify(COFFEE, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reproducible").value(false))
                .andExpect(jsonPath("$.diff.length()").value(1))
                .andExpect(jsonPath("$.diff[0].path").value("$.rateTo"))
                .andExpect(jsonPath("$.diff[0].expected").value(99.0))
                .andExpect(jsonPath("$.diff[0].actual").value(rateTo));

        mvc.perform(get(API + "/" + id).header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fork.reproducible").value(false));
    }

    @Test
    void changedRequestBreaksHash() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String snapshotId = latestSnapshotId(COFFEE, id);
        String request = column("score_request", snapshotId);
        updateColumn("score_request", snapshotId, request.replace("\"amount\":200000000", "\"amount\":200000001"));

        String json = body(verify(COFFEE, id).andExpect(jsonPath("$.reproducible").value(false)));
        List<String> paths = JsonPath.read(json, "$.diff[*].path");
        assertThat(paths).contains("requestHash");
    }

    @Test
    void usedLlmVerdictIsKeptInSnapshotAndModelIsNotAskedAgain() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        answerPurpose(id, "оснащение для цеха").andExpect(jsonPath(PURPOSE + ".status").value("CLOSED"));
        assertThat(purposeClient.calls()).isEqualTo(1);

        verify(COFFEE, id)
                .andExpect(jsonPath("$.reproducible").value(true))
                .andExpect(jsonPath("$.verdicts.length()").value(1))
                .andExpect(jsonPath("$.verdicts[0].scoringFactor").value("purpose"))
                .andExpect(jsonPath("$.verdicts[0].verdict.generatedBy").value("LLM"))
                .andExpect(jsonPath("$.verdicts[0].verdict.llm.model").value("qwen3:4b"))
                .andExpect(jsonPath("$.verdicts[0].verdict.llm.digest").value(CountingPurposeClient.LLM.digest()))
                .andExpect(jsonPath("$.verdicts[0].verdict.llm.promptVersion").value("bba46b2556f0"));

        assertThat(column("score_request", latestSnapshotId(COFFEE, id))).contains("\"purpose\":\"MATCH\"");
        assertThat(purposeClient.calls()).isEqualTo(1);
    }

    @Test
    void decisionReferencesSnapshotAndRequestHash() throws Exception {
        String id = evaluated(TECHSNAB, TECHSNAB_REQUEST);
        String json = body(mvc.perform(post(API + "/" + id + "/submit").header(DemoUserAuthFilter.HEADER, TECHSNAB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.decidedBy").value("SYSTEM")));
        String snapshotId = latestSnapshotId(TECHSNAB, id);

        assertThat((String) JsonPath.read(json, "$.decision.forkSnapshotId")).isEqualTo(snapshotId);
        assertThat((String) JsonPath.read(json, "$.decision.requestHash"))
                .isEqualTo(column("request_hash", snapshotId));
        verify(TECHSNAB, id).andExpect(jsonPath("$.reproducible").value(true));
    }

    @Test
    void snapshotWithoutTraceIsNotFound() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        updateColumn("score_request", latestSnapshotId(COFFEE, id), null);

        verify(COFFEE, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get(API + "/" + id).header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fork.factors").isEmpty());
    }

    @Test
    void draftWithoutSnapshotIsNotFound() throws Exception {
        String json = body(mvc.perform(post(API).header(DemoUserAuthFilter.HEADER, COFFEE)
                .contentType(MediaType.APPLICATION_JSON).content(COFFEE_REQUEST)));
        String id = JsonPath.read(json, "$.application.id");

        verify(COFFEE, id).andExpect(status().isNotFound());
    }

    @Test
    void otherClientCannotVerify() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);

        verify(TECHSNAB, id).andExpect(status().isForbidden());
    }

    private String evaluated(String user, String request) throws Exception {
        String json = body(mvc.perform(post(API).header(DemoUserAuthFilter.HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()));
        String id = JsonPath.read(json, "$.application.id");
        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserAuthFilter.HEADER, user))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions verify(String user, String id) throws Exception {
        return mvc.perform(post(API + "/" + id + "/fork/verify").header(DemoUserAuthFilter.HEADER, user));
    }

    private ResultActions answerPurpose(String id, String text) throws Exception {
        String json = body(mvc.perform(get(API + "/" + id).header(DemoUserAuthFilter.HEADER, COFFEE)));
        List<String> ids = JsonPath.read(json, PURPOSE + ".id");
        return mvc.perform(post(API + "/" + id + "/spots/" + ids.get(0) + "/close")
                .header(DemoUserAuthFilter.HEADER, COFFEE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"ANSWER\",\"text\":\"" + text + "\"}"));
    }

    private String latestSnapshotId(String user, String id) throws Exception {
        List<String> ids = JsonPath.read(body(mvc.perform(get(API + "/" + id + "/fork-history")
                .header(DemoUserAuthFilter.HEADER, user))), "$[*].id");
        return ids.get(ids.size() - 1);
    }

    private String column(String name, String snapshotId) {
        return jdbc.sql("SELECT " + name + " FROM fork_snapshot WHERE id = :id")
                .param("id", UUID.fromString(snapshotId))
                .query(String.class)
                .single();
    }

    private void updateColumn(String name, String snapshotId, String value) {
        jdbc.sql("UPDATE fork_snapshot SET " + name + " = :value WHERE id = :id")
                .param("value", value)
                .param("id", UUID.fromString(snapshotId))
                .update();
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
