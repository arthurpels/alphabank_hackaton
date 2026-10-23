package com.alphabank.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alphabank.backend.config.ApiPaths;
import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.DemoUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ApiPaths.BASE)
@Tag(name = "demo", description = "выбор роли и персонажа, текущий пользователь")
public class DemoController {

    private final DemoUserService demoUserService;

    public DemoController(DemoUserService demoUserService) {
        this.demoUserService = demoUserService;
    }

    @GetMapping("/demo/users")
    @SecurityRequirements
    @Operation(operationId = "listDemoUsers", summary = "Пользователи для стартового экрана")
    public List<MeDto> listDemoUsers(@RequestParam(required = false) UserRole role) {
        return demoUserService.getUsers(role);
    }

    @GetMapping("/me")
    @Operation(operationId = "getMe", summary = "Текущий пользователь и его компания")
    public MeDto getMe(CurrentUser user) {
        return demoUserService.findById(user.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Пользователь не найден"));
    }
}
