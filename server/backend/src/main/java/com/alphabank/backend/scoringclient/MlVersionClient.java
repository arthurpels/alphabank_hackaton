package com.alphabank.backend.scoringclient;

import java.util.Optional;

public interface MlVersionClient {

    Optional<String> engineVersion();
}
