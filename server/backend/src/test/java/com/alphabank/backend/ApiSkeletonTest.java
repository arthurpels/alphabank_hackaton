package com.alphabank.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.alphabank.backend.security.DemoUserInterceptor;
import com.alphabank.backend.stub.StubIds;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSkeletonTest {

    private static final String API = "/api/v1";
    private static final String CLIENT = StubIds.COFFEE_USER_ID.toString();
    private static final String UNDERWRITER = StubIds.UNDERWRITER_ID.toString();
    private static final String APP = API + "/applications/" + StubIds.COFFEE_APPLICATION_ID;

    @Autowired
    private MockMvc mvc;

    @Test
    void healthForDockerHealthcheck() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void demoUsersWithoutHeader() throws Exception {
        mvc.perform(get(API + "/demo/users").param("role", "UNDERWRITER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].company").isEmpty());
    }

    @Test
    void missingDemoUserHeaderIs401Problem() throws Exception {
        mvc.perform(get(API + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meReturnsClientWithCompany() throws Exception {
        mvc.perform(get(API + "/me").header(DemoUserInterceptor.HEADER, CLIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.company.inn").value("7701234567"));
    }

    @Test
    void applicationDetailsHasForkSpotsAndActions() throws Exception {
        mvc.perform(get(APP).header(DemoUserInterceptor.HEADER, CLIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("COLLECTING"))
                .andExpect(jsonPath("$.application.amount").value(200_000_000))
                .andExpect(jsonPath("$.application.availableActions").isArray())
                .andExpect(jsonPath("$.fork.rateFrom").value(17.0))
                .andExpect(jsonPath("$.fork.zone").value("UNDERWRITER"))
                .andExpect(jsonPath("$.spots.length()").value(4))
                .andExpect(jsonPath("$.decision").isEmpty());
    }

    @Test
    void submitReturnsFinalStatus() throws Exception {
        mvc.perform(post(APP + "/submit").header(DemoUserInterceptor.HEADER, CLIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("APPROVED"))
                .andExpect(jsonPath("$.offers[0].isRequested").value(true));
    }

    @Test
    void invalidCreateIs400Problem() throws Exception {
        mvc.perform(post(API + "/applications").header(DemoUserInterceptor.HEADER, CLIENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"EQUIPMENT\",\"amount\":100,\"termMonths\":24,"
                                + "\"goal\":\"витрина\",\"consents\":{\"bki\":true,\"personalData\":true}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void closeSpotChecksFieldsForMethod() throws Exception {
        mvc.perform(post(APP + "/spots/" + StubIds.COFFEE_APPLICATION_ID + "/close")
                        .header(DemoUserInterceptor.HEADER, CLIENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CONNECT_SOURCE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void clientCannotUseUnderwriterQueue() throws Exception {
        mvc.perform(get(API + "/underwriting/queue").header(DemoUserInterceptor.HEADER, CLIENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectWithoutReasonIs400() throws Exception {
        mvc.perform(post(APP + "/decision").header(DemoUserInterceptor.HEADER, UNDERWRITER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void underwriterSeesCalculatedOffers() throws Exception {
        mvc.perform(get(APP + "/offers").header(DemoUserInterceptor.HEADER, UNDERWRITER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CALCULATED"));
    }

    @Test
    void swaggerDocsAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/applications/{applicationId}/submit']").exists());
    }
}
