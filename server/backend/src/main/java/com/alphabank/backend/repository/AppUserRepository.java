package com.alphabank.backend.repository;

import com.alphabank.backend.dto.response.MeDto;

public interface AppUserRepository {

    void ensureExists(MeDto user);
}
