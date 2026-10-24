package com.alphabank.backend.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.alphabank.backend.support.FakeExternalServicesConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeExternalServicesConfig.class)
class SourceCheckIntegrationTest {

    private static final String API = "/api/v1/applications";
    private static final String COFFEE = StubIds.COFFEE_USER_ID.toString();
    private static final String TECHSNAB = StubIds.TAX_DEBT_USER_ID.toString();
    private static final String SHELL = StubIds.SHELL_USER_ID.toString();
    private static final String UNDERWRITER = StubIds.UNDERWRITER_ID.toString();
    private static final String WORKING_CAPITAL = """
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
    void coffeeIsConfirmedInEverySource() throws Exception {
        String id = evaluated(COFFEE, """
                {"productCode":"EQUIPMENT","amount":200000000,"termMonths":36,"goal":"витрина",
                 "consents":{"bki":true,"personalData":true}}
                """);

        checks(COFFEE, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedAt").isNotEmpty())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].source").value("EGRUL"))
                .andExpect(jsonPath("$.items[0].status").value("OK"))
                .andExpect(jsonPath("$.items[0].facts[0]").value("ООО «Кофейня», ИНН 7701234560"))
                .andExpect(jsonPath("$.items[0].facts[1]").value("статус: действует"))
                .andExpect(jsonPath("$.items[0].facts[2]").value("компания зарегистрирована 15.04.2019"))
                .andExpect(jsonPath("$.items[0].facts[3]").value("руководитель Смирнова А. В. с 15.04.2019"))
                .andExpect(jsonPath("$.items[0].problems.length()").value(0))
                .andExpect(jsonPath("$.items[1].source").value("FNS"))
                .andExpect(jsonPath("$.items[1].status").value("OK"))
                .andExpect(jsonPath("$.items[1].facts[0]").value("долга перед ФНС нет"))
                .andExpect(jsonPath("$.items[2].source").value("BKI"))
                .andExpect(jsonPath("$.items[2].status").value("OK"))
                .andExpect(jsonPath("$.items[2].facts[0]").value("кредитный рейтинг 712, просрочек за 12 мес. нет"));
    }

    @Test
    void unpaidTaxDebtIsWarning() throws Exception {
        String id = evaluated(TECHSNAB, WORKING_CAPITAL);

        checks(TECHSNAB, id)
                .andExpect(jsonPath("$.items[0].status").value("OK"))
                .andExpect(jsonPath("$.items[1].status").value("WARNING"))
                .andExpect(jsonPath("$.items[1].facts[0]").value("долг перед ФНС 3 412 ₽"))
                .andExpect(jsonPath("$.items[1].problems[0]").value("Долг в ФНС 3 412 ₽ — без погашения будет отказ"));
    }

    @Test
    void unremovableStopFactorsBlockRegistryCheck() throws Exception {
        String id = evaluated(SHELL, WORKING_CAPITAL);

        checks(SHELL, id)
                .andExpect(jsonPath("$.items[0].source").value("EGRUL"))
                .andExpect(jsonPath("$.items[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.items[0].problems.length()").value(2))
                .andExpect(jsonPath("$.items[0].problems[0]").value("Директор назначен меньше 3 месяцев назад"))
                .andExpect(jsonPath("$.items[0].problems[1]").value("Адрес регистрации массовый"));
    }

    @Test
    void checksNeedEvaluationAndOwnership() throws Exception {
        String draft = JsonPath.read(mvc.perform(post(API).header(DemoUserAuthFilter.HEADER, TECHSNAB)
                        .contentType(MediaType.APPLICATION_JSON).content(WORKING_CAPITAL))
                .andReturn().getResponse().getContentAsString(), "$.application.id");
        checks(TECHSNAB, draft).andExpect(status().isConflict());

        String id = evaluated(TECHSNAB, WORKING_CAPITAL);
        checks(COFFEE, id).andExpect(status().isForbidden());
        checks(UNDERWRITER, id).andExpect(status().isOk());
    }

    private String evaluated(String user, String body) throws Exception {
        String json = mvc.perform(post(API).header(DemoUserAuthFilter.HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.application.id");
        mvc.perform(post(API + "/" + id + "/evaluate").header(DemoUserAuthFilter.HEADER, user))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions checks(String user, String id) throws Exception {
        return mvc.perform(get(API + "/" + id + "/checks").header(DemoUserAuthFilter.HEADER, user));
    }
}
