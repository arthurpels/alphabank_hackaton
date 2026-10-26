package com.alphabank.backend.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.alphabank.backend.support.FakeExternalServicesConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeExternalServicesConfig.class)
class RulesIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rulesArePublicAndProxiedFromMl() throws Exception {
        mvc.perform(get("/api/v1/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesVersion").value("2026-10-26.draft2"))
                .andExpect(jsonPath("$.calcRatePct").value(15.0))
                .andExpect(jsonPath("$.products.length()").value(0))
                .andExpect(jsonPath("$.factors.length()").value(0));
    }
}
