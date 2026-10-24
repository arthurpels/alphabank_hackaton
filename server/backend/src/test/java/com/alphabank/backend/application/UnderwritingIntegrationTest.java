package com.alphabank.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.alphabank.backend.security.DemoUserInterceptor;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.support.FakeExternalServicesConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeExternalServicesConfig.class)
class UnderwritingIntegrationTest {

    private static final String API = "/api/v1";
    private static final String APPS = API + "/applications";
    private static final String BUILDER = StubIds.BUILDER_USER_ID.toString();
    private static final String COFFEE = StubIds.COFFEE_USER_ID.toString();
    private static final String UNDERWRITER = StubIds.UNDERWRITER_ID.toString();
    private static final String BUILDER_REQUEST = """
            {"productCode":"WORKING_CAPITAL","amount":300000000,"termMonths":12,
             "goal":"закупка материалов под договор подряда","consents":{"bki":true,"personalData":true}}
            """;
    private static final String COFFEE_REQUEST = """
            {"productCode":"EQUIPMENT","amount":200000000,"termMonths":36,"goal":"витрина",
             "consents":{"bki":true,"personalData":true}}
            """;
    private static final String CONTRACT_SPOT = """
            {"type":"DOCUMENT","title":"Договор с генподрядчиком",
             "clientHint":"Подтвердите будущие поступления. Нет договора — подойдёт акт сверки.",
             "closeMethod":"UPLOAD","scoringFactor":"debt_load"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MocksClient mocksClient;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void resetMocks() {
        mocksClient.reset();
    }

    @Test
    void builderGoesThroughInfoRequestToCounterOffer() throws Exception {
        String id = submitted(BUILDER, BUILDER_REQUEST);

        mvc.perform(get(API + "/underwriting/queue").header(DemoUserInterceptor.HEADER, UNDERWRITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationId == '" + id + "')].companyName")
                        .value("ИП Петров Сергей Николаевич"))
                .andExpect(jsonPath("$[?(@.applicationId == '" + id + "')].status").value("UNDERWRITING"));
        mvc.perform(get(API + "/underwriting/applications/" + id + "/summary")
                        .header(DemoUserInterceptor.HEADER, UNDERWRITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedBy").value("TEMPLATE"))
                .andExpect(jsonPath("$.items[0].text").value("кредитный рейтинг 655, просрочек за 12 мес. нет"))
                .andExpect(jsonPath("$.items[1].text").value("ИП зарегистрирован 01.03.2024"));

        String json = addSpot(id, CONTRACT_SPOT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.status").value("INFO_REQUESTED"))
                .andExpect(jsonPath("$.application.infoRequestedAt").isNotEmpty())
                .andExpect(jsonPath("$.application.infoRequestDeadline").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String contractSpot = spotId(json, "DOCUMENT");
        mvc.perform(get(APPS + "/" + id).header(DemoUserInterceptor.HEADER, BUILDER))
                .andExpect(jsonPath("$.spots[?(@.id == '" + contractSpot + "')].addedBy").value("UNDERWRITER"))
                .andExpect(jsonPath("$.spots[?(@.id == '" + contractSpot + "')].pricePp").value(3.5));

        String document = uploadedDocument(id);
        close(BUILDER, id, contractSpot, "{\"method\":\"UPLOAD\",\"documentId\":\"" + document + "\"}")
                .andExpect(status().isBadRequest());
        close(BUILDER, id, contractSpot, "{\"method\":\"UPLOAD\",\"documentId\":\"" + document
                + "\",\"text\":\"Договор подряда с ООО «СтройГрупп»\",\"contractAmount\":960000000,"
                + "\"contractTermMonths\":12}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"))
                .andExpect(jsonPath("$.spots[?(@.id == '" + contractSpot + "')].status").value("ON_REVIEW"))
                .andExpect(jsonPath("$.spots[?(@.id == '" + contractSpot + "')].lastAnswer.contractAmount")
                        .value(960000000));
        mvc.perform(get(APPS + "/" + id + "/offers").header(DemoUserInterceptor.HEADER, UNDERWRITER))
                .andExpect(jsonPath("$[*].status").value(everyItem(is("CALCULATED"))))
                .andExpect(jsonPath("$.length()").value(2));

        json = review(id, contractSpot, "{\"verdict\":\"ACCEPTED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"))
                .andExpect(jsonPath("$.spots[?(@.id == '" + contractSpot + "')].status").value("CLOSED"))
                .andExpect(jsonPath("$.spots[?(@.id == '" + contractSpot + "')].lastAnswer.reviewedBy")
                        .value(UNDERWRITER))
                .andExpect(jsonPath("$.spots[?(@.type == 'NO_DATA' && @.scoringFactor == 'debt_load')].status")
                        .value("CLOSED"))
                .andExpect(jsonPath("$.spots[?(@.scoringFactor == 'revenue_stability')].status").value("CLOSED"))
                .andExpect(jsonPath("$.fork.rateFrom").value(20.0))
                .andExpect(jsonPath("$.fork.rateTo").value(23.0))
                .andReturn().getResponse().getContentAsString();
        List<String> calculated = JsonPath.read(json, "$.offers[?(@.status == 'CALCULATED')].id");
        assertThat(calculated).hasSize(2);

        decide(id, "{\"type\":\"ALTERNATIVE\",\"offerIds\":[\"" + calculated.get(0)
                + "\"],\"comment\":\"Подходит вариант на 36 месяцев\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("COUNTER_OFFER"))
                .andExpect(jsonPath("$.decision.type").value("ALTERNATIVE"))
                .andExpect(jsonPath("$.decision.decidedBy").value("UNDERWRITER"))
                .andExpect(jsonPath("$.decision.decidedByUserId").value(UNDERWRITER));
        mvc.perform(get(APPS + "/" + id + "/offers").header(DemoUserInterceptor.HEADER, BUILDER))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(calculated.get(0)))
                .andExpect(jsonPath("$[0].status").value("OFFERED"))
                .andExpect(jsonPath("$[0].isManual").value(false));

        String history = mvc.perform(get(APPS + "/" + id + "/history").header(DemoUserInterceptor.HEADER, BUILDER))
                .andReturn().getResponse().getContentAsString();
        List<String> states = JsonPath.read(history, "$[?(@.event == 'STATUS_CHANGE')].toState");
        assertThat(states).containsSubsequence("UNDERWRITING", "INFO_REQUESTED", "UNDERWRITING", "COUNTER_OFFER");
        List<String> events = JsonPath.read(history, "$[*].event");
        assertThat(events).contains("SPOT_REVIEWED", "DECISION");
    }

    @Test
    void rejectedAnswerNeedsCommentAndReopensSpot() throws Exception {
        String id = submitted(BUILDER, BUILDER_REQUEST);
        String spot = spotId(addSpot(id, CONTRACT_SPOT).andReturn().getResponse().getContentAsString(), "DOCUMENT");
        close(BUILDER, id, spot, "{\"method\":\"UPLOAD\",\"documentId\":\"" + uploadedDocument(id)
                + "\",\"contractAmount\":960000000,\"contractTermMonths\":12}");

        review(id, spot, "{\"verdict\":\"REJECTED\"}").andExpect(status().isBadRequest());
        review(id, spot, "{\"verdict\":\"REJECTED\",\"comment\":\"На договоре нет подписи заказчика\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spots[?(@.id == '" + spot + "')].status").value("OPEN"))
                .andExpect(jsonPath("$.spots[?(@.id == '" + spot + "')].lastAnswer.verdict").value("REJECTED"))
                .andExpect(jsonPath("$.spots[?(@.id == '" + spot + "')].lastAnswer.reviewComment")
                        .value("На договоре нет подписи заказчика"));
        review(id, spot, "{\"verdict\":\"ACCEPTED\"}").andExpect(status().isConflict());
    }

    @Test
    void manualOfferNeedsOverrideAndWritesDiff() throws Exception {
        String id = submitted(COFFEE, COFFEE_REQUEST);
        String manual = "\"manualOffer\":{\"productCode\":\"EQUIPMENT\",\"amount\":150000000,\"termMonths\":36}";

        decide(id, "{\"type\":\"ALTERNATIVE\"," + manual + ",\"comment\":\"Снижаем сумму\"}")
                .andExpect(status().isBadRequest());
        decide(id, "{\"type\":\"ALTERNATIVE\"," + manual + ",\"override\":true,\"comment\":\"Снижаем сумму\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("COUNTER_OFFER"))
                .andExpect(jsonPath("$.decision.override").value(true));
        mvc.perform(get(APPS + "/" + id + "/offers").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(150000000))
                .andExpect(jsonPath("$[0].isManual").value(true))
                .andExpect(jsonPath("$[0].proposedBy").value("UNDERWRITER"))
                .andExpect(jsonPath("$[0].comment").value("Снижаем сумму"));

        String history = mvc.perform(get(APPS + "/" + id + "/history").header(DemoUserInterceptor.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString();
        List<Integer> before = JsonPath.read(history, "$[?(@.event == 'OVERRIDE')].payload.before.amount");
        List<Integer> after = JsonPath.read(history, "$[?(@.event == 'OVERRIDE')].payload.after.amount");
        assertThat(before).containsExactly(200000000);
        assertThat(after).containsExactly(150000000);
    }

    @Test
    void approvalCreatesOfferOnRequestedConditions() throws Exception {
        String id = submitted(COFFEE, COFFEE_REQUEST);

        decide(id, "{\"type\":\"APPROVED\",\"comment\":\"Устраивает\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("APPROVED"))
                .andExpect(jsonPath("$.decision.decidedBy").value("UNDERWRITER"))
                .andExpect(jsonPath("$.offers[?(@.status == 'OFFERED')].isRequested").value(true));
    }

    @Test
    void rejectionNeedsSpotOfThisApplication() throws Exception {
        String id = submitted(COFFEE, COFFEE_REQUEST);
        String collateral = JsonPath.read(mvc.perform(get(APPS + "/" + id).header(DemoUserInterceptor.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString(), "$.spots[3].id");

        decide(id, "{\"type\":\"REJECTED\",\"reasonSpotId\":\"" + UUID.randomUUID() + "\",\"comment\":\"Нет\"}")
                .andExpect(status().isBadRequest());
        decide(id, "{\"type\":\"REJECTED\",\"reasonSpotId\":\"" + collateral + "\",\"comment\":\"Без обеспечения нельзя\"}")
                .andExpect(jsonPath("$.application.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reasonSpotId").value(collateral));
    }

    @Test
    void underwriterActionsAreGuarded() throws Exception {
        String collecting = evaluated(COFFEE, COFFEE_REQUEST);
        decide(collecting, "{\"type\":\"APPROVED\"}").andExpect(status().isConflict());
        addSpot(collecting, CONTRACT_SPOT).andExpect(status().isConflict());

        String id = submitted(COFFEE, COFFEE_REQUEST);
        mvc.perform(post(APPS + "/" + id + "/decision").header(DemoUserInterceptor.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        addSpot(id, CONTRACT_SPOT.replace("\"DOCUMENT\"", "\"STOP_FACTOR\"")).andExpect(status().isBadRequest());
        addSpot(id, CONTRACT_SPOT.replace("debt_load", "liquidity")).andExpect(status().isBadRequest());
        String collateral = JsonPath.read(mvc.perform(get(APPS + "/" + id).header(DemoUserInterceptor.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString(), "$.spots[3].id");
        review(id, collateral, "{\"verdict\":\"ACCEPTED\"}").andExpect(status().isConflict());
    }

    private String evaluated(String user, String body) throws Exception {
        String json = mvc.perform(post(APPS).header(DemoUserInterceptor.HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.application.id");
        mvc.perform(post(APPS + "/" + id + "/evaluate").header(DemoUserInterceptor.HEADER, user))
                .andExpect(status().isOk());
        return id;
    }

    private String submitted(String user, String body) throws Exception {
        String id = evaluated(user, body);
        mvc.perform(post(APPS + "/" + id + "/submit").header(DemoUserInterceptor.HEADER, user))
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"));
        return id;
    }

    private String uploadedDocument(String applicationId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO document (id, application_id, kind, file_path) VALUES (:id, :app, 'CONTRACT_GP', 'x.pdf')")
                .param("id", id)
                .param("app", UUID.fromString(applicationId))
                .update();
        return id.toString();
    }

    private static String spotId(String json, String type) {
        List<String> ids = JsonPath.read(json, "$.spots[?(@.type == '" + type + "')].id");
        return ids.get(0);
    }

    private ResultActions addSpot(String id, String body) throws Exception {
        return mvc.perform(post(APPS + "/" + id + "/spots").header(DemoUserInterceptor.HEADER, UNDERWRITER)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions review(String id, String spotId, String body) throws Exception {
        return mvc.perform(post(APPS + "/" + id + "/spots/" + spotId + "/review")
                .header(DemoUserInterceptor.HEADER, UNDERWRITER)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions decide(String id, String body) throws Exception {
        return mvc.perform(post(APPS + "/" + id + "/decision").header(DemoUserInterceptor.HEADER, UNDERWRITER)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions close(String user, String id, String spotId, String body) throws Exception {
        return mvc.perform(post(APPS + "/" + id + "/spots/" + spotId + "/close")
                .header(DemoUserInterceptor.HEADER, user)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
