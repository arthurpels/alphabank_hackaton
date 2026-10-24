package com.alphabank.backend.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.security.DemoUserAuthFilter;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    private static final String DEMO_USER = "demoUser";

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Кредитный конвейер «Белые пятна» — публичный API")
                        .version("0.1.0")
                        .description("Контракт: docs/openapi.yaml. Деньги — в копейках, ставки — в % годовых."))
                .components(new Components().addSecuritySchemes(DEMO_USER, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(DemoUserAuthFilter.HEADER)
                        .description("id пользователя, выбранного на стартовом экране")))
                .addSecurityItem(new SecurityRequirement().addList(DEMO_USER));
    }
}
