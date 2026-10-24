package com.alphabank.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.alphabank.backend.config.ApiPaths;
import com.alphabank.backend.dto.request.SpotCloseRequest;
import com.alphabank.backend.dto.request.SpotCreateRequest;
import com.alphabank.backend.dto.request.SpotReviewRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.SpotDto;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.SpotService;
import com.alphabank.backend.service.UnderwriterSpotService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(ApiPaths.BASE + "/applications/{applicationId}/spots")
@Tag(name = "spots", description = "белые пятна и их закрытие")
public class SpotController {

    private final SpotService spotService;
    private final UnderwriterSpotService underwriterSpotService;

    public SpotController(SpotService spotService, UnderwriterSpotService underwriterSpotService) {
        this.spotService = spotService;
        this.underwriterSpotService = underwriterSpotService;
    }

    @GetMapping
    @Operation(operationId = "listSpots", summary = "Пятна заявки")
    public List<SpotDto> listSpots(CurrentUser user, @PathVariable UUID applicationId) {
        return spotService.getSpots(user, applicationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addSpot", summary = "Добавить пятно — дозапрос (UNDERWRITING → INFO_REQUESTED)")
    public ApplicationDetailsDto addSpot(CurrentUser user, @PathVariable UUID applicationId,
                                         @Valid @RequestBody SpotCreateRequest request) {
        return underwriterSpotService.add(user, applicationId, request);
    }

    @PostMapping("/{spotId}/close")
    @Operation(operationId = "closeSpot", summary = "Закрыть пятно")
    public ApplicationDetailsDto closeSpot(CurrentUser user, @PathVariable UUID applicationId,
                                           @PathVariable UUID spotId,
                                           @Valid @RequestBody SpotCloseRequest request) {
        return spotService.close(user, applicationId, spotId, request);
    }

    @PostMapping("/{spotId}/review")
    @Operation(operationId = "reviewSpot", summary = "Принять или не принять ответ по пятну")
    public ApplicationDetailsDto reviewSpot(CurrentUser user, @PathVariable UUID applicationId,
                                            @PathVariable UUID spotId,
                                            @Valid @RequestBody SpotReviewRequest request) {
        return underwriterSpotService.review(user, applicationId, spotId, request);
    }
}
