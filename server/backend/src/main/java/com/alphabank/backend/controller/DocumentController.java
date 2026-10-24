package com.alphabank.backend.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.alphabank.backend.config.ApiPaths;
import com.alphabank.backend.dto.response.DocumentDto;
import com.alphabank.backend.model.DocumentFile;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.DocumentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ApiPaths.BASE + "/applications/{applicationId}/documents")
@Tag(name = "spots", description = "белые пятна и их закрытие")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{documentId}")
    @Operation(operationId = "downloadDocument", summary = "Скачать документ")
    public ResponseEntity<byte[]> download(CurrentUser user, @PathVariable UUID applicationId,
                                           @PathVariable UUID documentId) {
        DocumentFile file = documentService.download(user, applicationId, documentId);
        String encodedName = URLEncoder.encode(file.document().fileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(file.content());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "uploadDocument", summary = "Загрузить документ")
    public DocumentDto uploadDocument(CurrentUser user, @PathVariable UUID applicationId,
                                      @RequestPart("file") MultipartFile file,
                                      @RequestParam("kind") String kind) {
        return documentService.upload(user, applicationId, file, kind);
    }
}
