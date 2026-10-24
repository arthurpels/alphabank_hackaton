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
import com.alphabank.backend.security.DemoUserInterceptor;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.support.FakeExternalServicesConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeExternalServicesConfig.class)
class IssuanceIntegrationTest {

    private static final String API = "/api/v1/applications";
    private static final String COFFEE = StubIds.COFFEE_USER_ID.toString();
    private static final String UNDERWRITER = StubIds.UNDERWRITER_ID.toString();
    private static final String COFFEE_REQUEST = """
            {"productCode":"EQUIPMENT","amount":200000000,"termMonths":36,"goal":"витрина",
             "consents":{"bki":true,"personalData":true}}
            """;
    private static final String BIG_REQUEST = """
            {"productCode":"EQUIPMENT","amount":800000000,"termMonths":36,"goal":"витрина",
             "consents":{"bki":true,"personalData":true}}
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
    void coffeeReachesIssuedWithPaymentSchedule() throws Exception {
        String id = approvedCoffee();
        String offer = JsonPath.read(details(id), "$.offers[0].id");

        accept(id, offer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("SIGNING"))
                .andExpect(jsonPath("$.application.availableActions[0]").value("SIGN"))
                .andExpect(jsonPath("$.offers[0].status").value("CHOSEN"))
                .andExpect(jsonPath("$.offers[0].chosenAt").isNotEmpty())
                .andExpect(jsonPath("$.contract.offerId").value(offer))
                .andExpect(jsonPath("$.contract.amount").value(200000000))
                .andExpect(jsonPath("$.contract.termMonths").value(36))
                .andExpect(jsonPath("$.contract.rate").value(19.5))
                .andExpect(jsonPath("$.contract.monthlyPayment").value(7381862))
                .andExpect(jsonPath("$.contract.signedAt").isEmpty())
                .andExpect(jsonPath("$.contract.issuedAt").isEmpty())
                .andExpect(jsonPath("$.contract.schedule.length()").value(36))
                .andExpect(jsonPath("$.contract.schedule[0].interest").value(3250000))
                .andExpect(jsonPath("$.contract.schedule[35].balance").value(0));

        String json = sign(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("ISSUED"))
                .andExpect(jsonPath("$.application.availableActions.length()").value(0))
                .andExpect(jsonPath("$.contract.issuedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(json, "$.contract.signedAt"))
                .isEqualTo(JsonPath.read(json, "$.contract.issuedAt"));

        String history = mvc.perform(get(API + "/" + id + "/history").header(DemoUserInterceptor.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString();
        List<String> states = JsonPath.read(history, "$[?(@.event == 'STATUS_CHANGE')].toState");
        assertThat(states).containsSubsequence("APPROVED", "SIGNING", "ISSUED");
        List<String> events = JsonPath.read(history, "$[*].event");
        assertThat(events).contains("OFFER_CHOSEN");
    }

    @Test
    void choosingCounterOfferDeclinesOthersAndLinksDecision() throws Exception {
        String id = submitted(BIG_REQUEST);
        String json = details(id);
        List<String> offers = JsonPath.read(json, "$.offers[*].id");
        assertThat(offers).hasSize(2);
        String overdraft = offers.get(1);

        accept(id, overdraft)
                .andExpect(jsonPath("$.application.status").value("SIGNING"))
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].productCode").value("OVERDRAFT"))
                .andExpect(jsonPath("$.contract.amount").value(12000000))
                .andExpect(jsonPath("$.contract.termMonths").value(12));

        String declined = jdbc.sql("SELECT status FROM offer WHERE id = :id")
                .param("id", UUID.fromString(offers.get(0))).query(String.class).single();
        UUID linked = jdbc.sql("SELECT offer_id FROM decision WHERE application_id = :id")
                .param("id", UUID.fromString(id)).query(UUID.class).single();
        assertThat(declined).isEqualTo("DECLINED");
        assertThat(linked).isEqualTo(UUID.fromString(overdraft));
    }

    @Test
    void acceptAndSignAreGuarded() throws Exception {
        String collecting = evaluated(COFFEE_REQUEST);
        accept(collecting, UUID.randomUUID().toString()).andExpect(status().isConflict());

        String id = submitted(BIG_REQUEST);
        List<String> offers = JsonPath.read(details(id), "$.offers[*].id");
        sign(id).andExpect(status().isConflict());
        accept(id, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        mvc.perform(post(API + "/" + id + "/accept").header(DemoUserInterceptor.HEADER, UNDERWRITER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"offerId\":\"" + offers.get(0) + "\"}"))
                .andExpect(status().isForbidden());

        accept(id, offers.get(0)).andExpect(status().isOk());
        accept(id, offers.get(1)).andExpect(status().isConflict());
    }

    @Test
    void declinedOfferCannotBeChosen() throws Exception {
        String id = submitted(BIG_REQUEST);
        List<String> offers = JsonPath.read(details(id), "$.offers[*].id");
        jdbc.sql("UPDATE offer SET status = 'DECLINED' WHERE id = :id")
                .param("id", UUID.fromString(offers.get(1))).update();

        accept(id, offers.get(1)).andExpect(status().isBadRequest());
    }

    @Test
    void cancelDeclinesOffersAndKeepsReason() throws Exception {
        String id = submitted(BIG_REQUEST);
        List<String> offers = JsonPath.read(details(id), "$.offers[*].id");

        cancel(id, "{\"reason\":\"Нашли деньги в другом месте\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("CANCELLED"))
                .andExpect(jsonPath("$.offers.length()").value(0));
        List<String> statuses = jdbc.sql("SELECT status FROM offer WHERE application_id = :id")
                .param("id", UUID.fromString(id)).query(String.class).list();
        assertThat(statuses).hasSize(offers.size()).containsOnly("DECLINED");
        String history = mvc.perform(get(API + "/" + id + "/history").header(DemoUserInterceptor.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> reasons = JsonPath.read(history, "$[?(@.toState == 'CANCELLED')].reason");
        assertThat(reasons).containsExactly("Нашли деньги в другом месте");
    }

    @Test
    void cancelIsAllowedOnlyBeforeSigning() throws Exception {
        cancel(evaluated(COFFEE_REQUEST), "{}").andExpect(jsonPath("$.application.status").value("CANCELLED"));

        String id = approvedCoffee();
        accept(id, JsonPath.read(details(id), "$.offers[0].id"));
        cancel(id, "{}").andExpect(status().isConflict());
    }

    private String approvedCoffee() throws Exception {
        String id = evaluated(COFFEE_REQUEST);
        String json = details(id);
        close(id, spotId(json, "debt_load"), "{\"method\":\"CONNECT_SOURCE\",\"sourceCode\":\"OFD\",\"consent\":true}");
        close(id, JsonPath.read(details(id), "$.spots[4].id"),
                "{\"method\":\"ANSWER\",\"optionCode\":\"RENOVATION\",\"text\":\"Был ремонт\"}");
        close(id, spotId(json, "purpose"), "{\"method\":\"ANSWER\",\"text\":\"2 кофемашины и витрина\"}");
        mvc.perform(post(API + "/" + id + "/submit").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(jsonPath("$.application.status").value("APPROVED"));
        return id;
    }

    private String submitted(String body) throws Exception {
        String id = evaluated(body);
        mvc.perform(post(API + "/" + id + "/submit").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(jsonPath("$.application.status").value("COUNTER_OFFER"));
        return id;
    }

    private String evaluated(String body) throws Exception {
        String json = mvc.perform(post(API).header(DemoUserInterceptor.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.application.id");
        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(status().isOk());
        return id;
    }

    private String details(String id) throws Exception {
        return mvc.perform(get(API + "/" + id).header(DemoUserInterceptor.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString();
    }

    private static String spotId(String json, String factor) {
        List<String> ids = JsonPath.read(json, "$.spots[?(@.scoringFactor == '" + factor + "')].id");
        return ids.get(0);
    }

    private void close(String id, String spotId, String body) throws Exception {
        mvc.perform(post(API + "/" + id + "/spots/" + spotId + "/close").header(DemoUserInterceptor.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private ResultActions accept(String id, String offerId) throws Exception {
        return mvc.perform(post(API + "/" + id + "/accept").header(DemoUserInterceptor.HEADER, COFFEE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"offerId\":\"" + offerId + "\"}"));
    }

    private ResultActions sign(String id) throws Exception {
        return mvc.perform(post(API + "/" + id + "/sign").header(DemoUserInterceptor.HEADER, COFFEE));
    }

    private ResultActions cancel(String id, String body) throws Exception {
        return mvc.perform(post(API + "/" + id + "/cancel").header(DemoUserInterceptor.HEADER, COFFEE)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
