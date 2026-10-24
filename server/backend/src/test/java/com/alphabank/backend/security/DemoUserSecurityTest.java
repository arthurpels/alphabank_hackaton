package com.alphabank.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.alphabank.backend.stub.StubIds;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DemoUserSecurityTest {

    private static final String API = "/api/v1";
    private static final String CLIENT = StubIds.COFFEE_USER_ID.toString();
    private static final String OTHER_CLIENT = StubIds.BUILDER_USER_ID.toString();
    private static final String UNDERWRITER = StubIds.UNDERWRITER_ID.toString();

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queueAvailableForUnderwriter() throws Exception {
        mvc.perform(get(API + "/underwriting/queue").header(DemoUserAuthFilter.HEADER, UNDERWRITER))
                .andExpect(status().isOk());
    }

    @Test
    void reviewForbiddenForClient() throws Exception {
        mvc.perform(post(API + "/applications/{app}/spots/{spot}/review",
                        StubIds.COFFEE_APPLICATION_ID, UUID.randomUUID())
                        .header(DemoUserAuthFilter.HEADER, CLIENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":\"ACCEPTED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void decisionForbiddenForClient() throws Exception {
        mvc.perform(post(API + "/applications/{app}/decision", UUID.randomUUID())
                        .header(DemoUserAuthFilter.HEADER, CLIENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"REJECTED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void addSpotForbiddenForClient() throws Exception {
        mvc.perform(post(API + "/applications/{app}/spots", UUID.randomUUID())
                        .header(DemoUserAuthFilter.HEADER, CLIENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DOCUMENT\",\"title\":\"договор\",\"clientHint\":\"загрузите\","
                                + "\"closeMethod\":\"UPLOAD\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void meAvailableForUnderwriterWithoutCompany() throws Exception {
        mvc.perform(get(API + "/me").header(DemoUserAuthFilter.HEADER, UNDERWRITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("UNDERWRITER"))
                .andExpect(jsonPath("$.company").isEmpty());
    }

    @Test
    void clientSeesOnlyOwnCompanyApplications() throws Exception {
        MvcResult created = mvc.perform(post(API + "/applications")
                        .header(DemoUserAuthFilter.HEADER, CLIENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"EQUIPMENT\",\"amount\":200000000,\"termMonths\":36,"
                                + "\"goal\":\"Кофемашины\",\"consents\":{\"bki\":true,\"personalData\":true}}"))
                .andExpect(status().isCreated())
                .andReturn();
        String applicationId = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .at("/application/id")
                .asText();

        mvc.perform(get(API + "/applications/{id}", applicationId).header(DemoUserAuthFilter.HEADER, OTHER_CLIENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get(API + "/applications/{id}", applicationId).header(DemoUserAuthFilter.HEADER, UNDERWRITER))
                .andExpect(status().isOk());
    }
}
