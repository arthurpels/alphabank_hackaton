package com.alphabank.backend.security;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.service.DemoUserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class DemoUserInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Demo-User";
    static final String ATTRIBUTE = CurrentUser.class.getName();

    private final DemoUserService users;

    public DemoUserInterceptor(DemoUserService users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        MeDto user = users.findById(parseHeader(request.getHeader(HEADER)))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Пользователь не найден"));
        UUID companyId = user.company() == null ? null : user.company().id();
        request.setAttribute(ATTRIBUTE, new CurrentUser(user.id(), user.role(), companyId));
        return true;
    }

    private static UUID parseHeader(String header) {
        if (header == null || header.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Не выбран пользователь: нет заголовка " + HEADER);
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Заголовок " + HEADER + " должен быть UUID");
        }
    }
}
