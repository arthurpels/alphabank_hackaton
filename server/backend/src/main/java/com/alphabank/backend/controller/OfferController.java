package com.alphabank.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alphabank.backend.config.ApiPaths;
import com.alphabank.backend.dto.response.OfferDto;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.OfferService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ApiPaths.BASE + "/applications/{applicationId}/offers")
@Tag(name = "offers", description = "варианты условий")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @GetMapping
    @Operation(operationId = "listOffers", summary = "Варианты условий")
    public List<OfferDto> listOffers(CurrentUser user, @PathVariable UUID applicationId) {
        return offerService.getOffers(user, applicationId);
    }
}
