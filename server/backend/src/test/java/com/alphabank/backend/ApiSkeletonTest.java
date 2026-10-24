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
                .andExpect(jsonPath("$.company.inn").value("7701234560"));
    }

    @Test
    void productsComeFromDatabase() throws Exception {
        mvc.perform(get(API + "/products").header(DemoUserInterceptor.HEADER, CLIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.code == 'EQUIPMENT')].minAmount").value(30_000_000))
                .andExpect(jsonPath("$[?(@.code == 'OVERDRAFT')].name").value("Овердрафт для бизнеса"));
    }

    @Test
    void submitOfUnknownApplicationIs404() throws Exception {
        mvc.perform(post(APP + "/submit").header(DemoUserInterceptor.HEADER, CLIENT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
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
    void closeSpotOfUnknownApplicationIs404() throws Exception {
        mvc.perform(post(APP + "/spots/" + StubIds.COFFEE_APPLICATION_ID + "/close")
                        .header(DemoUserInterceptor.HEADER, CLIENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CONNECT_SOURCE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
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
    void offersOfUnknownApplicationIs404() throws Exception {
        mvc.perform(get(APP + "/offers").header(DemoUserInterceptor.HEADER, UNDERWRITER))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerDocsAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/applications/{applicationId}/submit']").exists());
    }
}
