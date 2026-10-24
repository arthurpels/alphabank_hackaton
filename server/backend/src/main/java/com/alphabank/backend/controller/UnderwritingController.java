package com.alphabank.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alphabank.backend.config.ApiPaths;
import com.alphabank.backend.dto.request.DecisionRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.QueueItemDto;
import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.DecisionService;
import com.alphabank.backend.service.UnderwritingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(ApiPaths.BASE)
@Tag(name = "underwriting", description = "кабинет андеррайтера — эмуляция внутренней системы банка")
public class UnderwritingController {

    private final UnderwritingService underwritingService;
    private final DecisionService decisionService;

    public UnderwritingController(UnderwritingService underwritingService, DecisionService decisionService) {
        this.underwritingService = underwritingService;
        this.decisionService = decisionService;
    }

    @GetMapping("/underwriting/queue")
    @Operation(operationId = "getUnderwritingQueue", summary = "Очередь андеррайтера")
    public List<QueueItemDto> getUnderwritingQueue(CurrentUser user,
                                                   @RequestParam(required = false) ApplicationStatus status) {
        return underwritingService.getQueue(user, status);
    }

    @GetMapping("/underwriting/applications/{applicationId}/summary")
    @Operation(operationId = "getUnderwritingSummary", summary = "Сводка для андеррайтера")
    public UnderwritingSummaryDto getUnderwritingSummary(CurrentUser user, @PathVariable UUID applicationId) {
        return underwritingService.getSummary(user, applicationId);
    }

    @PostMapping("/applications/{applicationId}/decision")
    @Operation(operationId = "makeDecision", summary = "Вынести решение (UNDERWRITING → APPROVED | COUNTER_OFFER | REJECTED)")
    public ApplicationDetailsDto makeDecision(CurrentUser user, @PathVariable UUID applicationId,
                                              @Valid @RequestBody DecisionRequest request) {
        return decisionService.decide(user, applicationId, request);
    }
}
