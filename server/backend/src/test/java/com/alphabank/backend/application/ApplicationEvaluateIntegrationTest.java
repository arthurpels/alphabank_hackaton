package com.alphabank.backend.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.security.DemoUserInterceptor;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.support.FakeExternalServicesConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeExternalServicesConfig.class)
class ApplicationEvaluateIntegrationTest {

    private static final String API = "/api/v1/applications";
    private static final String COFFEE = StubIds.COFFEE_USER_ID.toString();
    private static final String BUILDER = StubIds.BUILDER_USER_ID.toString();
    private static final String UNDERWRITER = StubIds.UNDERWRITER_ID.toString();
    private static final String COFFEE_REQUEST = """
            {"productCode":"EQUIPMENT","amount":200000000,"termMonths":36,
             "goal":"2 кофемашины и витрина для второй точки",
             "consents":{"bki":true,"personalData":true}}
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void createdApplicationIsDraftWithoutFork() throws Exception {
        mvc.perform(post(API).header(DemoUserInterceptor.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content(COFFEE_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.status").value("DRAFT"))
                .andExpect(jsonPath("$.application.company.inn").value(FakeExternalServicesConfig.COFFEE_INN))
                .andExpect(jsonPath("$.application.product.code").value("EQUIPMENT"))
                .andExpect(jsonPath("$.application.consents.bki").value(true))
                .andExpect(jsonPath("$.application.availableActions[1]").value("EVALUATE"))
                .andExpect(jsonPath("$.fork").isEmpty())
                .andExpect(jsonPath("$.spots.length()").value(0));
    }

    @Test
    void coffeeGetsFork17to26AndFourSpots() throws Exception {
        String id = create();

        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("COLLECTING"))
                .andExpect(jsonPath("$.fork.rateFrom").value(17.0))
                .andExpect(jsonPath("$.fork.rateTo").value(26.0))
                .andExpect(jsonPath("$.fork.zone").value("UNDERWRITER"))
                .andExpect(jsonPath("$.fork.thresholds.approveMax").value(20.0))
                .andExpect(jsonPath("$.fork.rulesVersion").value("2026-10-23.draft1"))
                .andExpect(jsonPath("$.spots.length()").value(4))
                .andExpect(jsonPath("$.spots[0].title").value("Продажи через кассу не видны"))
                .andExpect(jsonPath("$.spots[0].pricePp").value(3.5))
                .andExpect(jsonPath("$.spots[1].scoringFactor").value("revenue_stability"))
                .andExpect(jsonPath("$.spots[3].type").value("COLLATERAL"))
                .andExpect(jsonPath("$.application.company.directorFio").value("Смирнова А. В."))
                .andExpect(jsonPath("$.application.availableActions[2]").value("SUBMIT"));

        mvc.perform(get(API + "/" + id + "/spots").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].title").value("Продажи через кассу не видны"));

        mvc.perform(get(API + "/" + id + "/history").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(jsonPath("$[0].toState").value("DRAFT"))
                .andExpect(jsonPath("$[1].fromState").value("DRAFT"))
                .andExpect(jsonPath("$[1].toState").value("COLLECTING"))
                .andExpect(jsonPath("$[2].event").value("SPOT_CREATED"));
    }

    @Test
    void editInCollectingRecalculatesFork() throws Exception {
        String id = create();
        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserInterceptor.HEADER, COFFEE));

        mvc.perform(patch(API + "/" + id).header(DemoUserInterceptor.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"termMonths\":24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.termMonths").value(24))
                .andExpect(jsonPath("$.application.status").value("COLLECTING"));

        mvc.perform(get(API + "/" + id + "/fork-history").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void evaluateTwiceIs409() throws Exception {
        String id = create();
        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserInterceptor.HEADER, COFFEE));

        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("COLLECTING"));
    }

    @Test
    void evaluateWithoutBkiConsentIs400() throws Exception {
        String id = create(COFFEE_REQUEST.replace("\"bki\":true", "\"bki\":false"));

        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void termOutsideProductIs400() throws Exception {
        mvc.perform(post(API).header(DemoUserInterceptor.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COFFEE_REQUEST.replace("\"termMonths\":36", "\"termMonths\":18")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherCompanyCannotSeeApplicationButUnderwriterCan() throws Exception {
        String id = create();

        mvc.perform(get(API + "/" + id).header(DemoUserInterceptor.HEADER, BUILDER))
                .andExpect(status().isForbidden());
        mvc.perform(get(API + "/" + id).header(DemoUserInterceptor.HEADER, UNDERWRITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.availableActions.length()").value(0));
    }

    @Test
    void clientSeesOwnApplicationsInList() throws Exception {
        String id = create();

        mvc.perform(get(API).header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void unknownApplicationIs404() throws Exception {
        mvc.perform(get(API + "/00000000-0000-0000-0000-0000000000ff").header(DemoUserInterceptor.HEADER, COFFEE))
                .andExpect(status().isNotFound());
    }

    private String create() throws Exception {
        return create(COFFEE_REQUEST);
    }

    private String create(String body) throws Exception {
        String json = mvc.perform(post(API).header(DemoUserInterceptor.HEADER, COFFEE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.application.id");
    }
}
