package com.alphabank.backend.service;

import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.alphabank.backend.dto.response.DocumentDto;
import com.alphabank.backend.security.CurrentUser;

public interface DocumentService {

    DocumentDto upload(CurrentUser user, UUID applicationId, MultipartFile file, String kind);
}
