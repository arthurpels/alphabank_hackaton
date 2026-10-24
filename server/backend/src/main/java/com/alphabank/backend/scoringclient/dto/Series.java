package com.alphabank.backend.scoringclient.dto;

import java.util.List;

import com.alphabank.backend.enums.SourceCode;

public record Series(SourceCode source, List<SeriesPoint> points) {
}
