package com.alphabank.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.alphabank.backend.dto.response.DocumentDto;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.DocumentService;

@Service
public class DocumentServiceImpl implements DocumentService {

    @Override
    public DocumentDto upload(CurrentUser user, UUID applicationId, MultipartFile file, String kind) {
        user.requireRole(UserRole.CLIENT);
        if (file.isEmpty()) {
            throw ApiException.validation("Файл пустой");
        }
        UUID id = UUID.nameUUIDFromBytes((applicationId + file.getOriginalFilename()).getBytes(StandardCharsets.UTF_8));
        return new DocumentDto(id, kind, file.getOriginalFilename(), OffsetDateTime.now());
    }
}
