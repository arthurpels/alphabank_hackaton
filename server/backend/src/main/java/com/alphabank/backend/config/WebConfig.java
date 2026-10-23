package com.alphabank.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.alphabank.backend.security.CurrentUserArgumentResolver;
import com.alphabank.backend.security.DemoUserInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final DemoUserInterceptor demoUserInterceptor;
    private final CurrentUserArgumentResolver currentUserResolver;
    private final String[] corsAllowedOrigins;

    public WebConfig(DemoUserInterceptor demoUserInterceptor,
                     CurrentUserArgumentResolver currentUserResolver,
                     @Value("${app.cors-allowed-origins}") String[] corsAllowedOrigins) {
        this.demoUserInterceptor = demoUserInterceptor;
        this.currentUserResolver = currentUserResolver;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(demoUserInterceptor)
                .addPathPatterns(ApiPaths.BASE + "/**")
                .excludePathPatterns(ApiPaths.BASE + "/demo/users");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(ApiPaths.BASE + "/**")
                .allowedOrigins(corsAllowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
                .allowedHeaders("*");
    }
}
