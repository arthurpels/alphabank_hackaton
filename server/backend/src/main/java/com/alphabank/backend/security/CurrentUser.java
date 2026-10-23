package com.alphabank.backend.security;

import java.util.UUID;

import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;

public record CurrentUser(UUID id, UserRole role, UUID companyId) {

    public boolean isUnderwriter() {
        return role == UserRole.UNDERWRITER;
    }

    public void requireRole(UserRole required) {
        if (role != required) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    required == UserRole.UNDERWRITER
                            ? "Действие доступно только андеррайтеру"
                            : "Действие доступно только клиенту");
        }
    }
}
