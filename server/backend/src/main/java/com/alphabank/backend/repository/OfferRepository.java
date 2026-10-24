package com.alphabank.backend.repository;

import java.util.UUID;

public interface OfferRepository {

    int expireActive(UUID applicationId);
}
