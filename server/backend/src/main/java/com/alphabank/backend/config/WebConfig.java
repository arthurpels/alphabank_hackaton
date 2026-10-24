package com.alphabank.backend.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.alphabank.backend.security.CurrentUserArgumentResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserResolver;
    private final ApplicationMdcInterceptor applicationMdcInterceptor;

    public WebConfig(CurrentUserArgumentResolver currentUserResolver,
                     ApplicationMdcInterceptor applicationMdcInterceptor) {
        this.currentUserResolver = currentUserResolver;
        this.applicationMdcInterceptor = applicationMdcInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(applicationMdcInterceptor).addPathPatterns(ApiPaths.BASE + "/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
