package com.alphabank.backend.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.alphabank.backend.config.ApiPaths;
import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.service.DemoUserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class DemoUserAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Demo-User";
    static final String ATTRIBUTE = CurrentUser.class.getName();
    private static final String DEMO_USERS_PATH = ApiPaths.BASE + "/demo/users";

    private final DemoUserService users;
    private final ObjectMapper objectMapper;

    public DemoUserAuthFilter(DemoUserService users, ObjectMapper objectMapper) {
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(ApiPaths.BASE + "/") || path.equals(DEMO_USERS_PATH)
                || path.startsWith(ApiPaths.BASE + "/demo/time") || path.equals(ApiPaths.BASE + "/rules");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Не выбран пользователь: нет заголовка " + HEADER);
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Заголовок " + HEADER + " должен быть UUID");
            return;
        }
        MeDto user = users.findById(id).orElse(null);
        if (user == null) {
            writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Пользователь не найден");
            return;
        }
        UUID companyId = user.company() == null ? null : user.company().id();
        CurrentUser currentUser = new CurrentUser(user.id(), user.role(), companyId);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                currentUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(ATTRIBUTE, currentUser);
        chain.doFilter(request, response);
    }

    public static void writeProblem(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
                                    String code, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
