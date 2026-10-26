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
    private static final long COFFEE_LOAN_PAYMENT = 7381862L;
    private static final String BUILDER_REQUEST = """
            {"productCode":"WORKING_CAPITAL","amount":300000000,"termMonths":12,
             "goal":"закупка материалов под договор подряда","consents":{"bki":true,"personalData":true}}
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

        String history = mvc.perform(get(API + "/" + id + "/history").header(DemoUserAuthFilter.HEADER, COFFEE))
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
        mvc.perform(post(API + "/" + id + "/accept").header(DemoUserAuthFilter.HEADER, UNDERWRITER)
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
        String history = mvc.perform(get(API + "/" + id + "/history").header(DemoUserAuthFilter.HEADER, COFFEE))
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

    @Test
    void issuedLoanPaymentIsPassedToScoringOfNextApplication() throws Exception {
        String first = issuedCoffee();

        String second = evaluated(COFFEE_REQUEST);

        mvc.perform(get(API + "/" + second).header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(jsonPath("$.fork.existingPayment").value(COFFEE_LOAN_PAYMENT));
        mvc.perform(post(API + "/" + second + "/fork/verify").header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(jsonPath("$.reproducible").value(true));
        mvc.perform(get(API + "/" + first).header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(jsonPath("$.fork.existingPayment").value(0));
        List<String> secondSummary = summaryTexts(second);
        assertThat(secondSummary.get(1)).isEqualTo("Действующие платежи по кредитам: 73 818,62 ₽ в месяц "
                + "(договоры нашего банка 73 818,62 ₽) — учитываются в долговой нагрузке.");
        assertThat(summaryTexts(first)).noneMatch(text -> text.startsWith("Действующие платежи"));
    }

    @Test
    void loanInSigningAlreadyCountsAsObligation() throws Exception {
        String first = approvedCoffee();
        accept(first, JsonPath.read(details(first), "$.offers[0].id")).andExpect(status().isOk());

        String second = evaluated(COFFEE_REQUEST);

        mvc.perform(get(API + "/" + second).header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(jsonPath("$.fork.existingPayment").value(COFFEE_LOAN_PAYMENT));
    }

    @Test
    void approvedButNotAcceptedLoanDoesNotCount() throws Exception {
        approvedCoffee();

        String second = evaluated(COFFEE_REQUEST);

        mvc.perform(get(API + "/" + second).header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(jsonPath("$.fork.existingPayment").value(0));
    }

    @Test
    void loanOfAnotherCompanyDoesNotCount() throws Exception {
        issuedCoffee();

        String json = mvc.perform(post(API).header(DemoUserAuthFilter.HEADER, StubIds.BUILDER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(BUILDER_REQUEST))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.application.id");
        mvc.perform(post(API + "/" + id + "/evaluate")
                        .header(DemoUserAuthFilter.HEADER, StubIds.BUILDER_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fork.existingPayment").value(0));
    }

    private List<String> summaryTexts(String id) throws Exception {
        String json = mvc.perform(get("/api/v1/underwriting/applications/" + id + "/summary")
                        .header(DemoUserAuthFilter.HEADER, UNDERWRITER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.items[*].text");
    }

    private String issuedCoffee() throws Exception {
        String id = approvedCoffee();
        accept(id, JsonPath.read(details(id), "$.offers[0].id")).andExpect(status().isOk());
        sign(id).andExpect(jsonPath("$.application.status").value("ISSUED"));
        return id;
    }

    private String approvedCoffee() throws Exception {
        String id = evaluated(COFFEE_REQUEST);
        String json = details(id);
        close(id, spotId(json, "debt_load"), "{\"method\":\"CONNECT_SOURCE\",\"sourceCode\":\"OFD\",\"consent\":true}");
        close(id, JsonPath.read(details(id), "$.spots[4].id"),
                "{\"method\":\"ANSWER\",\"optionCode\":\"RENOVATION\",\"text\":\"Был ремонт\"}");
        close(id, spotId(json, "purpose"), "{\"method\":\"ANSWER\",\"text\":\"2 кофемашины и витрина\"}");
        mvc.perform(post(API + "/" + id + "/submit").header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(jsonPath("$.application.status").value("APPROVED"));
        return id;
    }

    private String submitted(String body) throws Exception {
        String id = evaluated(body);
        mvc.perform(post(API + "/" + id + "/submit").header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(jsonPath("$.application.status").value("COUNTER_OFFER"));
        return id;
    }

    private String evaluated(String body) throws Exception {
        String json = mvc.perform(post(API).header(DemoUserAuthFilter.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.application.id");
        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserAuthFilter.HEADER, COFFEE))
                .andExpect(status().isOk());
        return id;
    }

    private String details(String id) throws Exception {
        return mvc.perform(get(API + "/" + id).header(DemoUserAuthFilter.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString();
    }

    private static String spotId(String json, String factor) {
        List<String> ids = JsonPath.read(json, "$.spots[?(@.scoringFactor == '" + factor + "')].id");
        return ids.get(0);
    }

    private void close(String id, String spotId, String body) throws Exception {
        mvc.perform(post(API + "/" + id + "/spots/" + spotId + "/close").header(DemoUserAuthFilter.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private ResultActions accept(String id, String offerId) throws Exception {
        return mvc.perform(post(API + "/" + id + "/accept").header(DemoUserAuthFilter.HEADER, COFFEE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"offerId\":\"" + offerId + "\"}"));
    }

    private ResultActions sign(String id) throws Exception {
        return mvc.perform(post(API + "/" + id + "/sign").header(DemoUserAuthFilter.HEADER, COFFEE));
    }

    private ResultActions cancel(String id, String body) throws Exception {
        return mvc.perform(post(API + "/" + id + "/cancel").header(DemoUserAuthFilter.HEADER, COFFEE)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
