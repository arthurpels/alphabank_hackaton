package com.alphabank.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.enums.UserRole;

public interface DemoUserService {

    List<MeDto> getUsers(UserRole role);

    Optional<MeDto> findById(UUID id);
}
