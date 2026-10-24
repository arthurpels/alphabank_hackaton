package com.alphabank.backend.closer;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.repository.DocumentRepository;

@Component
public class UploadCloser implements SpotCloser {

    private final DocumentRepository documentRepository;
    private final SpotResolutionWriter writer;

    public UploadCloser(DocumentRepository documentRepository, SpotResolutionWriter writer) {
        this.documentRepository = documentRepository;
        this.writer = writer;
    }

    @Override
    public CloseMethod method() {
        return CloseMethod.UPLOAD;
    }

    @Override
    public void close(SpotCloseContext context) {
        if (!documentRepository.existsForApplication(context.request().documentId(), context.application().id())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Документ не найден — сначала загрузите файл");
        }
        writer.sendToReview(context.spot(), context.user().id(), null, context.request().text(),
                context.request().documentId());
    }
}
