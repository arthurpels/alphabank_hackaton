package com.alphabank.backend.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApplicationMdcInterceptor implements HandlerInterceptor {

    private static final Pattern APPLICATION_ID = Pattern.compile("/applications/([0-9a-fA-F-]{36})");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Matcher matcher = APPLICATION_ID.matcher(request.getRequestURI());
        if (matcher.find()) {
            MDC.put("applicationId", matcher.group(1));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        MDC.clear();
    }
}
