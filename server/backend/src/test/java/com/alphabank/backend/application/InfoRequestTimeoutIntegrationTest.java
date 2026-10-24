package com.alphabank.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.security.DemoUserInterceptor;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.support.FakeExternalServicesConfig;
import com.alphabank.backend.util.DemoClock;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeExternalServicesConfig.class)
class InfoRequestTimeoutIntegrationTest {

    private static final String API = "/api/v1";
    private static final String APPS = API + "/applications";
    private static final String BUILDER = StubIds.BUILDER_USER_ID.toString();
    private static final String UNDERWRITER = StubIds.UNDERWRITER_ID.toString();
    private static final String BUILDER_REQUEST = """
            {"productCode":"WORKING_CAPITAL","amount":300000000,"termMonths":12,
             "goal":"закупка материалов под договор подряда","consents":{"bki":true,"personalData":true}}
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
    private DemoClock clock;

    @BeforeEach
    void resetMocks() {
        mocksClient.reset();
        clock.reset();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void unansweredInfoRequestIsCancelledAfterFiveDays() throws Exception {
        String id = infoRequested();
        String json = details(BUILDER, id);
        OffsetDateTime requestedAt = OffsetDateTime.parse(JsonPath.read(json, "$.application.infoRequestedAt"));
        OffsetDateTime deadline = OffsetDateTime.parse(JsonPath.read(json, "$.application.infoRequestDeadline"));
        assertThat(deadline).isEqualTo(requestedAt.plusDays(5));

        advance(4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftedDays").value(4))
                .andExpect(jsonPath("$.cancelledApplicationIds.length()").value(0));
        mvc.perform(get(APPS + "/" + id).header(DemoUserInterceptor.HEADER, BUILDER))
                .andExpect(jsonPath("$.application.status").value("INFO_REQUESTED"));

        advance(1)
                .andExpect(jsonPath("$.shiftedDays").value(5))
                .andExpect(jsonPath("$.cancelledApplicationIds[0]").value(id));
        mvc.perform(get(APPS + "/" + id).header(DemoUserInterceptor.HEADER, BUILDER))
                .andExpect(jsonPath("$.application.status").value("CANCELLED"))
                .andExpect(jsonPath("$.application.availableActions.length()").value(0));

        String history = mvc.perform(get(APPS + "/" + id + "/history").header(DemoUserInterceptor.HEADER, BUILDER))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> from = JsonPath.read(history, "$[?(@.toState == 'CANCELLED')].fromState");
        List<String> reasons = JsonPath.read(history, "$[?(@.toState == 'CANCELLED')].reason");
        List<Object> users = JsonPath.read(history, "$[?(@.toState == 'CANCELLED')].userId");
        assertThat(from).containsExactly("INFO_REQUESTED");
        assertThat(reasons).containsExactly("Нет ответа на дозапрос 5 дней");
        assertThat(users).containsOnlyNulls();
    }

    @Test
    void answeredInfoRequestIsNotCancelled() throws Exception {
        String id = infoRequested();
        List<String> documentSpots = JsonPath.read(details(BUILDER, id), "$.spots[?(@.type == 'DOCUMENT')].id");
        String spot = documentSpots.get(0);
        String document = upload(id);

        mvc.perform(post(APPS + "/" + id + "/spots/" + spot + "/close").header(DemoUserInterceptor.HEADER, BUILDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"UPLOAD\",\"documentId\":\"" + document
                                + "\",\"contractAmount\":960000000,\"contractTermMonths\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"));

        advance(6).andExpect(jsonPath("$.cancelledApplicationIds.length()").value(0));
        mvc.perform(get(APPS + "/" + id).header(DemoUserInterceptor.HEADER, BUILDER))
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"));
    }

    @Test
    void demoTimeCanBeReadAndReset() throws Exception {
        mvc.perform(get(API + "/demo/time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftedDays").value(0));
        advance(0).andExpect(status().isBadRequest());
        advance(31).andExpect(status().isBadRequest());
        advance(2).andExpect(jsonPath("$.shiftedDays").value(2));

        mvc.perform(post(API + "/demo/time/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftedDays").value(0));
    }

    private String infoRequested() throws Exception {
        String json = mvc.perform(post(APPS).header(DemoUserInterceptor.HEADER, BUILDER)
                        .contentType(MediaType.APPLICATION_JSON).content(BUILDER_REQUEST))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.application.id");
        mvc.perform(post(APPS + "/" + id + "/evaluate").header(DemoUserInterceptor.HEADER, BUILDER))
                .andExpect(status().isOk());
        mvc.perform(post(APPS + "/" + id + "/submit").header(DemoUserInterceptor.HEADER, BUILDER))
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"));
        mvc.perform(post(APPS + "/" + id + "/spots").header(DemoUserInterceptor.HEADER, UNDERWRITER)
                        .contentType(MediaType.APPLICATION_JSON).content(CONTRACT_SPOT))
                .andExpect(jsonPath("$.application.status").value("INFO_REQUESTED"));
        return id;
    }

    private String upload(String id) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf",
                "%PDF-1.4 договор подряда".getBytes(StandardCharsets.UTF_8));
        String json = mvc.perform(multipart(APPS + "/" + id + "/documents").file(file).param("kind", "CONTRACT_GP")
                        .header(DemoUserInterceptor.HEADER, BUILDER))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String details(String user, String id) throws Exception {
        return mvc.perform(get(APPS + "/" + id).header(DemoUserInterceptor.HEADER, user))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private ResultActions advance(int days) throws Exception {
        return mvc.perform(post(API + "/demo/time/advance").contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":" + days + "}"));
    }
}
