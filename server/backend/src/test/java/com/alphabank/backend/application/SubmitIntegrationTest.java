package com.alphabank.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.alphabank.backend.security.DemoUserInterceptor;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.support.FakeExternalServicesConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeExternalServicesConfig.class)
class SubmitIntegrationTest {

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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MocksClient mocksClient;

    @BeforeEach
    void resetMocks() {
        mocksClient.reset();
    }

    @Test
    void coffeeWithClosedSpotsIsApprovedOnRequestedConditions() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String json = details(COFFEE, id);
        close(COFFEE, id, spotId(json, "debt_load"), "{\"method\":\"CONNECT_SOURCE\",\"sourceCode\":\"OFD\",\"consent\":true}");
        close(COFFEE, id, JsonPath.read(details(COFFEE, id), "$.spots[4].id"),
                "{\"method\":\"ANSWER\",\"optionCode\":\"RENOVATION\",\"text\":\"Был ремонт\"}");
        close(COFFEE, id, spotId(json, "purpose"), "{\"method\":\"ANSWER\",\"text\":\"2 кофемашины и витрина\"}");

        submit(COFFEE, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("APPROVED"))
                .andExpect(jsonPath("$.application.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.decision.type").value("APPROVED"))
                .andExpect(jsonPath("$.decision.decidedBy").value("SYSTEM"))
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].isRequested").value(true))
                .andExpect(jsonPath("$.offers[0].status").value("OFFERED"))
                .andExpect(jsonPath("$.offers[0].amount").value(200000000))
                .andExpect(jsonPath("$.offers[0].fork.rateTo").value(19.5));

        String history = mvc.perform(get(API + "/" + id + "/fork-history").header(DemoUserInterceptor.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString();
        List<Double> rateTo = JsonPath.read(history, "$[*].rateTo");
        assertThat(rateTo).containsExactly(26.0, 23.5, 21.0, 19.5, 19.5);
    }

    @Test
    void forkCrossingThresholdGoesToUnderwritingWithHiddenOffers() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);

        submit(COFFEE, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"))
                .andExpect(jsonPath("$.decision").isEmpty())
                .andExpect(jsonPath("$.offers.length()").value(0));

        mvc.perform(get(API + "/" + id + "/offers").header(DemoUserInterceptor.HEADER, UNDERWRITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("CALCULATED"))
                .andExpect(jsonPath("$[0].amount").value(120000000))
                .andExpect(jsonPath("$[0].isRequested").value(false));
        mvc.perform(get(API + "/" + id + "/offers").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void failedPolicyMakesSystemCounterOffer() throws Exception {
        String id = evaluated(COFFEE, """
                {"productCode":"EQUIPMENT","amount":800000000,"termMonths":36,"goal":"витрина",
                 "consents":{"bki":true,"personalData":true}}
                """);

        submit(COFFEE, id)
                .andExpect(jsonPath("$.application.status").value("COUNTER_OFFER"))
                .andExpect(jsonPath("$.decision.type").value("ALTERNATIVE"))
                .andExpect(jsonPath("$.decision.comment").value("Система предлагает иные условия: вариантов 2"))
                .andExpect(jsonPath("$.offers.length()").value(2))
                .andExpect(jsonPath("$.offers[0].status").value("OFFERED"))
                .andExpect(jsonPath("$.offers[0].amount").value(480000000))
                .andExpect(jsonPath("$.offers[0].termMonths").value(36))
                .andExpect(jsonPath("$.offers[0].fork.thresholds.approveMax").value(20.0))
                .andExpect(jsonPath("$.offers[1].productCode").value("OVERDRAFT"))
                .andExpect(jsonPath("$.offers[1].fork.thresholds.approveMax").value(22.0));
    }

    @Test
    void unpaidTaxDebtRejectsWithStopFactorAsReason() throws Exception {
        String id = evaluated(TECHSNAB, TECHSNAB_REQUEST);
        String stop = JsonPath.read(details(TECHSNAB, id), "$.spots[0].id");

        submit(TECHSNAB, id)
                .andExpect(jsonPath("$.application.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.type").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reasonSpotId").value(stop))
                .andExpect(jsonPath("$.offers.length()").value(0));
    }

    @Test
    void paidTaxDebtNoLongerBlocks() throws Exception {
        String id = evaluated(TECHSNAB, TECHSNAB_REQUEST);
        close(TECHSNAB, id, JsonPath.read(details(TECHSNAB, id), "$.spots[0].id"), "{\"method\":\"PAY\"}");

        submit(TECHSNAB, id)
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"));
    }

    @Test
    void stopFactorAppearedAfterEvaluateRejects() throws Exception {
        String id = evaluated(TECHSNAB, TECHSNAB_REQUEST);
        close(TECHSNAB, id, JsonPath.read(details(TECHSNAB, id), "$.spots[0].id"), "{\"method\":\"PAY\"}");
        mocksClient.reset();

        String json = submit(TECHSNAB, id)
                .andExpect(jsonPath("$.application.status").value("REJECTED"))
                .andReturn().getResponse().getContentAsString();
        List<String> open = JsonPath.read(json, "$.spots[?(@.type == 'STOP_FACTOR' && @.status == 'OPEN')].id");
        assertThat(open).hasSize(1);
        assertThat((String) JsonPath.read(json, "$.decision.reasonSpotId")).isEqualTo(open.get(0));
    }

    @Test
    void stopFactorGoneAfterEvaluateIsClosedBySystem() throws Exception {
        String id = evaluated(TECHSNAB, TECHSNAB_REQUEST);
        mocksClient.fnsPay(FakeExternalServicesConfig.TECHSNAB_INN);

        submit(TECHSNAB, id)
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"))
                .andExpect(jsonPath("$.spots[0].type").value("STOP_FACTOR"))
                .andExpect(jsonPath("$.spots[0].status").value("CLOSED"));
    }

    @Test
    void submitIsAllowedOnlyOnceAndNotInDraft() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        submit(COFFEE, id).andExpect(status().isOk());
        submit(COFFEE, id).andExpect(status().isConflict());

        String draft = JsonPath.read(mvc.perform(post(API).header(DemoUserInterceptor.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content(COFFEE_REQUEST))
                .andReturn().getResponse().getContentAsString(), "$.application.id");
        submit(COFFEE, draft).andExpect(status().isConflict());
    }

    @Test
    void historyShowsAutoCheckPath() throws Exception {
        String id = evaluated(TECHSNAB, TECHSNAB_REQUEST);
        submit(TECHSNAB, id);

        String history = mvc.perform(get(API + "/" + id + "/history").header(DemoUserInterceptor.HEADER, TECHSNAB))
                .andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(history, "$[*].event");
        assertThat(types).contains("DECISION");
        List<String> statuses = JsonPath.read(history, "$[?(@.event == 'STATUS_CHANGE')].toState");
        assertThat(statuses).contains("SUBMITTED", "AUTO_CHECK", "REJECTED");
    }

    private String evaluated(String user, String body) throws Exception {
        String json = mvc.perform(post(API).header(DemoUserInterceptor.HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.application.id");
        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserInterceptor.HEADER, user))
                .andExpect(status().isOk());
        return id;
    }

    private String details(String user, String id) throws Exception {
        return mvc.perform(get(API + "/" + id).header(DemoUserInterceptor.HEADER, user))
                .andReturn().getResponse().getContentAsString();
    }

    private static String spotId(String json, String factor) {
        List<String> ids = JsonPath.read(json, "$.spots[?(@.scoringFactor == '" + factor + "')].id");
        return ids.get(0);
    }

    private ResultActions submit(String user, String id) throws Exception {
        return mvc.perform(post(API + "/" + id + "/submit").header(DemoUserInterceptor.HEADER, user));
    }

    private void close(String user, String id, String spotId, String body) throws Exception {
        mvc.perform(post(API + "/" + id + "/spots/" + spotId + "/close")
                        .header(DemoUserInterceptor.HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
