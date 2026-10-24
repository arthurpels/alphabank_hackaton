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
class SpotCloseIntegrationTest {

    private static final String API = "/api/v1/applications";
    private static final String COFFEE = StubIds.COFFEE_USER_ID.toString();
    private static final String TECHSNAB = StubIds.TAX_DEBT_USER_ID.toString();
    private static final String COFFEE_REQUEST = """
            {"productCode":"EQUIPMENT","amount":200000000,"termMonths":36,"goal":"витрина",
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
    void coffeeForkNarrowsStepByStepToAutoApprovalZone() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String json = details(COFFEE, id);

        close(COFFEE, id, spotId(json, "debt_load"), "{\"method\":\"CONNECT_SOURCE\",\"sourceCode\":\"OFD\",\"consent\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fork.rateFrom").value(18.0))
                .andExpect(jsonPath("$.fork.rateTo").value(23.5))
                .andExpect(jsonPath("$.spots[?(@.scoringFactor == 'debt_load')].status").value("CLOSED"))
                .andExpect(jsonPath("$.spots[1].status").value("CLOSED"))
                .andExpect(jsonPath("$.spots[4].type").value("ANOMALY"))
                .andExpect(jsonPath("$.spots[4].title").value("Почему в марте выручка упала на 45 %"))
                .andExpect(jsonPath("$.spots[4].answerOptions[0].code").value("RENOVATION"));

        json = details(COFFEE, id);
        close(COFFEE, id, JsonPath.read(json, "$.spots[4].id"),
                "{\"method\":\"ANSWER\",\"optionCode\":\"RENOVATION\",\"text\":\"Был ремонт, точка закрывалась на 2 недели\"}")
                .andExpect(jsonPath("$.spots[4].status").value("CLOSED"))
                .andExpect(jsonPath("$.spots[4].lastAnswer.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.spots[4].lastAnswer.reviewedBy").value("SYSTEM"))
                .andExpect(jsonPath("$.fork.rateTo").value(21.0));

        close(COFFEE, id, spotId(json, "purpose"), "{\"method\":\"ANSWER\",\"text\":\"2 кофемашины и витрина\"}")
                .andExpect(jsonPath("$.fork.rateFrom").value(18.0))
                .andExpect(jsonPath("$.fork.rateTo").value(19.5))
                .andExpect(jsonPath("$.fork.zone").value("APPROVE"))
                .andExpect(jsonPath("$.application.status").value("COLLECTING"));

        String history = mvc.perform(get(API + "/" + id + "/fork-history").header(DemoUserInterceptor.HEADER, COFFEE))
                .andReturn().getResponse().getContentAsString();
        List<Double> rateTo = JsonPath.read(history, "$[*].rateTo");
        assertThat(rateTo).containsExactly(26.0, 23.5, 21.0, 19.5);
    }

    @Test
    void unclearPurposeKeepsSpotOpenWithComment() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String purpose = spotId(details(COFFEE, id), "purpose");

        close(COFFEE, id, purpose, "{\"method\":\"ANSWER\",\"text\":\"на всякое разное\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spots[?(@.scoringFactor == 'purpose')].status").value("OPEN"))
                .andExpect(jsonPath("$.spots[?(@.scoringFactor == 'purpose')].lastAnswer.verdict").value("REJECTED"));
    }

    @Test
    void unverifiableAnswerGoesToReview() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        close(COFFEE, id, spotId(details(COFFEE, id), "debt_load"),
                "{\"method\":\"CONNECT_SOURCE\",\"sourceCode\":\"OFD\",\"consent\":true}");
        String anomaly = JsonPath.read(details(COFFEE, id), "$.spots[4].id");

        close(COFFEE, id, anomaly, "{\"method\":\"ANSWER\",\"optionCode\":\"CLIENT_LOST\"}")
                .andExpect(jsonPath("$.spots[4].status").value("ON_REVIEW"))
                .andExpect(jsonPath("$.spots[4].lastAnswer.verdict").value("PENDING"))
                .andExpect(jsonPath("$.fork.rateTo").value(23.5));
    }

    @Test
    void guaranteeClosesCollateralButPledgeGoesToReview() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String collateral = spotId(details(COFFEE, id), "collateral");

        close(COFFEE, id, collateral, "{\"method\":\"ADD_COLLATERAL\",\"collateralType\":\"GUARANTEE\"}")
                .andExpect(jsonPath("$.spots[3].status").value("CLOSED"))
                .andExpect(jsonPath("$.fork.rateFrom").value(17.0))
                .andExpect(jsonPath("$.fork.rateTo").value(24.5));

        String other = evaluated(COFFEE, COFFEE_REQUEST);
        close(COFFEE, other, spotId(details(COFFEE, other), "collateral"),
                "{\"method\":\"ADD_COLLATERAL\",\"collateralType\":\"PLEDGE\"}")
                .andExpect(jsonPath("$.spots[3].status").value("ON_REVIEW"));
    }

    @Test
    void taxDebtIsClosedByPayment() throws Exception {
        String id = evaluated(TECHSNAB, """
                {"productCode":"WORKING_CAPITAL","amount":80000000,"termMonths":12,"goal":"запчасти",
                 "consents":{"bki":true,"personalData":true}}
                """);
        String stop = JsonPath.read(details(TECHSNAB, id), "$.spots[0].id");

        close(TECHSNAB, id, stop, "{\"method\":\"PAY\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spots[0].type").value("STOP_FACTOR"))
                .andExpect(jsonPath("$.spots[0].status").value("CLOSED"));
    }

    @Test
    void sourceWithoutDataIs404AndSpotStaysOpen() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String debtLoad = spotId(details(COFFEE, id), "debt_load");

        close(COFFEE, id, debtLoad, "{\"method\":\"CONNECT_SOURCE\",\"sourceCode\":\"MARKETPLACE\",\"consent\":true}")
                .andExpect(status().isNotFound());
        mvc.perform(get(API + "/" + id).header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(jsonPath("$.spots[0].status").value("OPEN"));
    }

    @Test
    void wrongMethodIs400AndClosedSpotIs409() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String collateral = spotId(details(COFFEE, id), "collateral");

        close(COFFEE, id, collateral, "{\"method\":\"PAY\"}").andExpect(status().isBadRequest());
        close(COFFEE, id, collateral, "{\"method\":\"ADD_COLLATERAL\",\"collateralType\":\"DECLINED\"}")
                .andExpect(status().isOk());
        close(COFFEE, id, collateral, "{\"method\":\"ADD_COLLATERAL\",\"collateralType\":\"DECLINED\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void uploadOnAnswerSpotIs400() throws Exception {
        String id = evaluated(COFFEE, COFFEE_REQUEST);
        String purpose = spotId(details(COFFEE, id), "purpose");

        close(COFFEE, id, purpose, "{\"method\":\"UPLOAD\",\"documentId\":\"00000000-0000-0000-0000-0000000000aa\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void closingInDraftIs409() throws Exception {
        String json = mvc.perform(post(API).header(DemoUserInterceptor.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content(COFFEE_REQUEST))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.application.id");

        close(COFFEE, id, "00000000-0000-0000-0000-0000000000bb", "{\"method\":\"PAY\"}")
                .andExpect(status().isConflict());
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

    private ResultActions close(String user, String id, String spotId, String body) throws Exception {
        return mvc.perform(post(API + "/" + id + "/spots/" + spotId + "/close")
                .header(DemoUserInterceptor.HEADER, user)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
