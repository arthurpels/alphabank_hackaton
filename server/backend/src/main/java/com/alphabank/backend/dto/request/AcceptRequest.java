package com.alphabank.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AcceptRequest(@NotNull UUID offerId) {
}
