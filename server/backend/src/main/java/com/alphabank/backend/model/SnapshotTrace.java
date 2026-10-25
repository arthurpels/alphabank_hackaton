package com.alphabank.backend.model;

import java.util.List;

public record SnapshotTrace(
        String scoreRequest,
        String requestHash,
        String scoreResponse,
        String engineVersion,
        List<UsedVerdict> verdicts) {
}
