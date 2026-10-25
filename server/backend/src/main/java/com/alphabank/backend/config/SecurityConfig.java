package com.alphabank.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.alphabank.backend.security.DemoUserAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, DemoUserAuthFilter demoUserAuthFilter,
                                    CorsConfigurationSource corsConfigurationSource, ObjectMapper objectMapper)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                ApiPaths.BASE + "/demo/users", ApiPaths.BASE + "/demo/time",
                                ApiPaths.BASE + "/demo/time/**", ApiPaths.BASE + "/rules").permitAll()
                        .requestMatchers(ApiPaths.BASE + "/underwriting/**").hasRole("UNDERWRITER")
                        .requestMatchers(HttpMethod.POST, ApiPaths.BASE + "/applications/*/decision")
                        .hasRole("UNDERWRITER")
                        .requestMatchers(HttpMethod.POST, ApiPaths.BASE + "/applications/*/spots")
                        .hasRole("UNDERWRITER")
                        .requestMatchers(HttpMethod.POST, ApiPaths.BASE + "/applications/*/spots/*/review")
                        .hasRole("UNDERWRITER")
                        .anyRequest().authenticated())
                .addFilterBefore(demoUserAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, authException) -> DemoUserAuthFilter
                                .writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                        "Не выбран пользователь: нет заголовка " + DemoUserAuthFilter.HEADER))
                        .accessDeniedHandler((request, response, accessDeniedException) -> DemoUserAuthFilter
                                .writeProblem(response, objectMapper, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "Действие доступно только андеррайтеру")));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-allowed-origins}") String[] allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ApiPaths.BASE + "/**", configuration);
        return source;
    }
}
