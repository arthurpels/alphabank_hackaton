package com.alphabank.backend.service;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.scoringclient.dto.Series;

public interface RevenueSeriesService {

    Series fetch(SourceCode source, String inn);

    List<Series> fetchConnected(UUID applicationId, String inn);
}
