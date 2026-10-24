package com.alphabank.backend.service.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.alphabank.backend.dto.response.DocumentDto;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.model.DocumentFile;
import com.alphabank.backend.repository.DocumentRepository;
import com.alphabank.backend.repository.model.DocumentRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.DocumentService;
import com.alphabank.backend.storage.LocalDocumentStorage;

@Service
public class DocumentServiceImpl implements DocumentService {

    static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png");

    private final ApplicationAccessService accessService;
    private final DocumentRepository documentRepository;
    private final LocalDocumentStorage storage;

    public DocumentServiceImpl(ApplicationAccessService accessService, DocumentRepository documentRepository,
                               LocalDocumentStorage storage) {
        this.accessService = accessService;
        this.documentRepository = documentRepository;
        this.storage = storage;
    }

    @Override
    public DocumentDto upload(CurrentUser user, UUID applicationId, MultipartFile file, String kind) {
        user.requireRole(UserRole.CLIENT);
        accessService.load(user, applicationId);
        if (kind == null || kind.isBlank()) {
            throw ApiException.validation("Укажите kind документа");
        }
        if (file.isEmpty()) {
            throw ApiException.validation("Файл пустой");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw ApiException.validation("Допустимые форматы файла: PDF, JPEG, PNG");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.validation("Максимальный размер файла — 10 МБ");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("Не удалось прочитать файл");
        }
        UUID id = UUID.randomUUID();
        String originalName = originalName(file);
        Path path = storage.store(id, extension, content);
        DocumentRecord record = new DocumentRecord(id, applicationId, kind.trim(), path.toString(),
                originalName, contentType, OffsetDateTime.now());
        documentRepository.save(record);
        return new DocumentDto(id, record.kind(), originalName, record.uploadedAt());
    }

    @Override
    public DocumentFile download(CurrentUser user, UUID applicationId, UUID documentId) {
        accessService.load(user, applicationId);
        DocumentRecord record = documentRepository.findById(documentId)
                .filter(document -> document.applicationId().equals(applicationId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Документ не найден"));
        DocumentDto dto = new DocumentDto(record.id(), record.kind(), record.originalName(), record.uploadedAt());
        return new DocumentFile(dto, record.contentType(), storage.load(Path.of(record.filePath())));
    }

    private static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "document";
        }
        return Path.of(name).getFileName().toString();
    }
}
