package com.alphabank.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "CORS_ALLOWED_ORIGINS=http://localhost:3000,http://demo.test:5173")
@AutoConfigureMockMvc
class CorsConfigTest {

    private static final String API = "/api/v1/me";

    @Autowired
    private MockMvc mvc;

    @Test
    void allowsEveryConfiguredOrigin() throws Exception {
        preflight("http://localhost:3000")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
        preflight("http://demo.test:5173")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://demo.test:5173"));
    }

    @Test
    void rejectsDefaultOriginWhenOverridden() throws Exception {
        preflight("http://localhost:5173")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private ResultActions preflight(String origin) throws Exception {
        return mvc.perform(options(API)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"));
    }
}
