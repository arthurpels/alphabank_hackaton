package com.alphabank.backend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.alphabank.backend.dto.response.DocumentDto;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.model.DocumentFile;
import com.alphabank.backend.repository.DocumentRepository;
import com.alphabank.backend.repository.model.DocumentRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.impl.DocumentServiceImpl;
import com.alphabank.backend.storage.LocalDocumentStorage;

class DocumentServiceImplTest {

    private final ApplicationAccessService accessService = mock(ApplicationAccessService.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final LocalDocumentStorage storage = mock(LocalDocumentStorage.class);
    private final DocumentServiceImpl service = new DocumentServiceImpl(accessService, documentRepository, storage);

    private final UUID applicationId = UUID.randomUUID();
    private final CurrentUser client = new CurrentUser(UUID.randomUUID(), UserRole.CLIENT, UUID.randomUUID());

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "договор.pdf", "application/pdf", new byte[0]);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(client, applicationId, file, "CONTRACT_GP"));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.code());
        verifyNoInteractions(storage, documentRepository);
    }

    @Test
    void uploadRejectsUnsupportedType() {
        MockMultipartFile file = new MockMultipartFile("file", "report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1});

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(client, applicationId, file, "CONTRACT_GP"));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.code());
        verifyNoInteractions(storage, documentRepository);
    }

    @Test
    void uploadStoresFileAndRow() {
        byte[] content = "%PDF-1.4 demo".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "договор с генподрядчиком.pdf",
                "application/pdf", content);
        when(storage.store(any(), eq("pdf"), eq(content))).thenReturn(Path.of("id.pdf"));

        DocumentDto dto = service.upload(client, applicationId, file, " CONTRACT_GP ");

        verify(storage).store(dto.id(), "pdf", content);
        ArgumentCaptor<DocumentRecord> captor = ArgumentCaptor.forClass(DocumentRecord.class);
        verify(documentRepository).save(captor.capture());
        DocumentRecord saved = captor.getValue();
        assertEquals(dto.id(), saved.id());
        assertEquals(applicationId, saved.applicationId());
        assertEquals("CONTRACT_GP", saved.kind());
        assertEquals("id.pdf", saved.filePath());
        assertEquals("договор с генподрядчиком.pdf", saved.originalName());
        assertEquals("application/pdf", saved.contentType());
        assertEquals("CONTRACT_GP", dto.kind());
        assertEquals("договор с генподрядчиком.pdf", dto.fileName());
    }

    @Test
    void uploadRejectsMissingKind() {
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1});

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(client, applicationId, file, " "));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.code());
        verifyNoInteractions(storage, documentRepository);
    }

    @Test
    void downloadReturnsContent() {
        UUID documentId = UUID.randomUUID();
        byte[] content = "%PDF".getBytes();
        DocumentRecord record = new DocumentRecord(documentId, applicationId, "CONTRACT_GP", "id.pdf",
                "договор.pdf", "application/pdf", OffsetDateTime.now());
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(record));
        when(storage.load(Path.of("id.pdf"))).thenReturn(content);

        DocumentFile file = service.download(client, applicationId, documentId);

        assertArrayEquals(content, file.content());
        assertEquals("application/pdf", file.contentType());
        assertEquals("договор.pdf", file.document().fileName());
    }

    @Test
    void downloadRejectsDocumentOfAnotherApplication() {
        UUID documentId = UUID.randomUUID();
        DocumentRecord record = new DocumentRecord(documentId, UUID.randomUUID(), "CONTRACT_GP", "id.pdf",
                "договор.pdf", "application/pdf", OffsetDateTime.now());
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(record));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.download(client, applicationId, documentId));

        assertEquals(ErrorCode.NOT_FOUND, exception.code());
        verifyNoInteractions(storage);
    }
}
