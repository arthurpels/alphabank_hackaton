package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.service.DemoUserService;
import com.alphabank.backend.stub.StubUsers;

@Service
public class DemoUserServiceImpl implements DemoUserService {

    @Override
    public List<MeDto> getUsers(UserRole role) {
        return StubUsers.users().stream()
                .filter(user -> role == null || user.role() == role)
                .toList();
    }

    @Override
    public Optional<MeDto> findById(UUID id) {
        return StubUsers.users().stream().filter(user -> user.id().equals(id)).findFirst();
    }
}
