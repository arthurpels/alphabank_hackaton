package com.alphabank.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alphabank.backend.config.ApiPaths;
import com.alphabank.backend.scoringclient.RulesClient;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ApiPaths.BASE)
@Tag(name = "catalog", description = "справочники")
public class RulesController {

    private final RulesClient rulesClient;

    public RulesController(RulesClient rulesClient) {
        this.rulesClient = rulesClient;
    }

    @GetMapping("/rules")
    @Operation(operationId = "getScoringRules", summary = "Действующие правила скоринга")
    public JsonNode rules() {
        return rulesClient.rules();
    }
}
