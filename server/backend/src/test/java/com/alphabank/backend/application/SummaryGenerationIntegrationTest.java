package com.alphabank.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.security.DemoUserAuthFilter;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.support.CountingSummaryClient;
import com.alphabank.backend.support.FakeExternalServicesConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = "app.summary.llm-enabled=true")
@AutoConfigureMockMvc
@Import(FakeExternalServicesConfig.class)
class SummaryGenerationIntegrationTest {

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
             "clientHint":"Подтвердите будущие поступления.","closeMethod":"UPLOAD","scoringFactor":"debt_load"}
            """;

    private final List<UUID> applicationIds = new ArrayList<>();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MocksClient mocksClient;

    @Autowired
    private CountingSummaryClient summaryClient;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void reset() {
        mocksClient.reset();
        summaryClient.reset();
    }

    @AfterEach
    void cleanup() {
        summaryClient.resume();
        if (!applicationIds.isEmpty()) {
            jdbc.sql("TRUNCATE TABLE application CASCADE").update();
        }
    }

    @Test
    void storedTemplateIsFastWhileLlmRunsAndIsReplacedWhenReady() throws Exception {
        summaryClient.useLlm();
        summaryClient.pauseNext();
        String id = submitted();

        long started = System.nanoTime();
        mvc.perform(get(API + "/underwriting/applications/" + id + "/summary")
                        .header(DemoUserAuthFilter.HEADER, UNDERWRITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("generating"))
                .andExpect(jsonPath("$.generatedBy").value("TEMPLATE"))
                .andExpect(jsonPath("$.items[0].text").value(
                        "Вилка 18,0–27,0 % пересекает порог одобрения 20,0 % — решение зависит от открытых пятен."))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty());
        assertThat(System.nanoTime() - started).isLessThan(1_000_000_000L);

        mvc.perform(post(APPS + "/" + id + "/spots").header(DemoUserAuthFilter.HEADER, UNDERWRITER)
                        .contentType(MediaType.APPLICATION_JSON).content(CONTRACT_SPOT))
                .andExpect(status().isCreated());
        summaryClient.resume();

        String llm = awaitSummary(id, "ready", "Договор с генподрядчиком");
        assertThat((String) JsonPath.read(llm, "$.generatedBy")).isEqualTo("LLM");
        assertThat((String) JsonPath.read(llm, "$.items[0].text")).isEqualTo("Вывод подготовлен LLM");
        assertThat((String) JsonPath.read(llm, "$.llm.model")).isEqualTo("qwen3:4b");
        assertThat((String) JsonPath.read(llm, "$.llm.promptVersion")).isEqualTo("summary-v1");
        assertThat(llm).contains("Договор с генподрядчиком");
        assertThat(summaryClient.calls()).isGreaterThanOrEqualTo(2);
    }

    private String submitted() throws Exception {
        String created = mvc.perform(post(APPS).header(DemoUserAuthFilter.HEADER, BUILDER)
                        .contentType(MediaType.APPLICATION_JSON).content(BUILDER_REQUEST))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.application.id");
        applicationIds.add(UUID.fromString(id));
        mvc.perform(post(APPS + "/" + id + "/evaluate").header(DemoUserAuthFilter.HEADER, BUILDER))
                .andExpect(status().isOk());
        mvc.perform(post(APPS + "/" + id + "/submit").header(DemoUserAuthFilter.HEADER, BUILDER))
                .andExpect(jsonPath("$.application.status").value("UNDERWRITING"));
        return id;
    }

    private String awaitSummary(String id, String expectedStatus, String text) throws Exception {
        String response = "";
        for (int i = 0; i < 50; i++) {
            response = mvc.perform(get(API + "/underwriting/applications/" + id + "/summary")
                            .header(DemoUserAuthFilter.HEADER, UNDERWRITER))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            String actualStatus = JsonPath.read(response, "$.status");
            if (expectedStatus.equals(actualStatus) && (text == null || response.contains(text))) {
                return response;
            }
            Thread.sleep(100);
        }
        return response;
    }
}
