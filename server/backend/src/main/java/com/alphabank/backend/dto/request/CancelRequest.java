package com.alphabank.backend.dto.request;

import jakarta.validation.constraints.Size;

public record CancelRequest(@Size(max = 500) String reason) {
}
