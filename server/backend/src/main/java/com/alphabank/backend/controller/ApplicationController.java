package com.alphabank.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.alphabank.backend.config.ApiPaths;
import com.alphabank.backend.dto.request.AcceptRequest;
import com.alphabank.backend.dto.request.ApplicationCreateRequest;
import com.alphabank.backend.dto.request.ApplicationUpdateRequest;
import com.alphabank.backend.dto.request.CancelRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.ApplicationSummaryDto;
import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.dto.response.ForkDto;
import com.alphabank.backend.dto.response.SourceChecksDto;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationService;
import com.alphabank.backend.service.IssuanceService;
import com.alphabank.backend.service.SourceCheckService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(ApiPaths.BASE + "/applications")
@Tag(name = "applications", description = "заявка клиента — создание, расчёт, подача, оформление")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final IssuanceService issuanceService;
    private final SourceCheckService sourceCheckService;

    public ApplicationController(ApplicationService applicationService, IssuanceService issuanceService,
                                 SourceCheckService sourceCheckService) {
        this.applicationService = applicationService;
        this.issuanceService = issuanceService;
        this.sourceCheckService = sourceCheckService;
    }

    @GetMapping
    @Operation(operationId = "listApplications", summary = "Заявки текущего клиента")
    public List<ApplicationSummaryDto> listApplications(CurrentUser user) {
        return applicationService.getApplications(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createApplication", summary = "Создать заявку (→ DRAFT)")
    public ApplicationDetailsDto createApplication(CurrentUser user,
                                                   @Valid @RequestBody ApplicationCreateRequest request) {
        return applicationService.create(user, request);
    }

    @GetMapping("/{applicationId}")
    @Operation(operationId = "getApplication", summary = "Заявка целиком")
    public ApplicationDetailsDto getApplication(CurrentUser user, @PathVariable UUID applicationId) {
        return applicationService.getApplication(user, applicationId);
    }

    @PatchMapping("/{applicationId}")
    @Operation(operationId = "updateApplication", summary = "Изменить параметры заявки (DRAFT, COLLECTING)")
    public ApplicationDetailsDto updateApplication(CurrentUser user, @PathVariable UUID applicationId,
                                                   @Valid @RequestBody ApplicationUpdateRequest request) {
        return applicationService.update(user, applicationId, request);
    }

    @PostMapping("/{applicationId}/evaluate")
    @Operation(operationId = "evaluateApplication", summary = "Рассчитать (DRAFT → COLLECTING)")
    public ApplicationDetailsDto evaluateApplication(CurrentUser user, @PathVariable UUID applicationId) {
        return applicationService.evaluate(user, applicationId);
    }

    @GetMapping("/{applicationId}/checks")
    @Operation(operationId = "getApplicationChecks", summary = "Проверка компании в ЕГРЮЛ, ФНС и БКИ")
    public SourceChecksDto getApplicationChecks(CurrentUser user, @PathVariable UUID applicationId) {
        return sourceCheckService.check(user, applicationId);
    }

    @GetMapping("/{applicationId}/fork-history")
    @Operation(operationId = "getForkHistory", summary = "История вилки")
    public List<ForkDto> getForkHistory(CurrentUser user, @PathVariable UUID applicationId) {
        return applicationService.getForkHistory(user, applicationId);
    }

    @PostMapping("/{applicationId}/submit")
    @Operation(operationId = "submitApplication", summary = "Отправить заявку (COLLECTING → … → итоговый статус)")
    public ApplicationDetailsDto submitApplication(CurrentUser user, @PathVariable UUID applicationId) {
        return applicationService.submit(user, applicationId);
    }

    @PostMapping("/{applicationId}/accept")
    @Operation(operationId = "acceptOffer", summary = "Принять условия (APPROVED | COUNTER_OFFER → SIGNING)")
    public ApplicationDetailsDto acceptOffer(CurrentUser user, @PathVariable UUID applicationId,
                                             @Valid @RequestBody AcceptRequest request) {
        return issuanceService.accept(user, applicationId, request);
    }

    @PostMapping("/{applicationId}/sign")
    @Operation(operationId = "signContract", summary = "Подписать договор (SIGNING → ISSUED, смоделировано)")
    public ApplicationDetailsDto signContract(CurrentUser user, @PathVariable UUID applicationId) {
        return issuanceService.sign(user, applicationId);
    }

    @PostMapping("/{applicationId}/cancel")
    @Operation(operationId = "cancelApplication", summary = "Отменить заявку или отказаться от условий (→ CANCELLED)")
    public ApplicationDetailsDto cancelApplication(CurrentUser user, @PathVariable UUID applicationId,
                                                   @Valid @RequestBody(required = false) CancelRequest request) {
        return applicationService.cancel(user, applicationId, request);
    }

    @GetMapping("/{applicationId}/history")
    @Operation(operationId = "getApplicationHistory", summary = "Журнал заявки")
    public List<AuditEventDto> getApplicationHistory(CurrentUser user, @PathVariable UUID applicationId) {
        return applicationService.getHistory(user, applicationId);
    }
}
