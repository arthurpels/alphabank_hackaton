package com.alphabank.backend.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.alphabank.backend.enrichment.client.MockClientException;
import com.alphabank.backend.enrichment.client.MockNotFoundException;
import com.alphabank.backend.enrichment.client.MockValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException e) {
        ProblemDetail problem = problem(e.code(), e.getMessage());
        if (e.currentStatus() != null) {
            problem.setProperty("currentStatus", e.currentStatus());
        }
        return problem;
    }

    @ExceptionHandler(MockValidationException.class)
    public ProblemDetail handleMockValidation(MockValidationException e) {
        return problem(ErrorCode.VALIDATION_ERROR, e.getMessage());
    }

    @ExceptionHandler(MockNotFoundException.class)
    public ProblemDetail handleMockNotFound(MockNotFoundException e) {
        return problem(ErrorCode.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({MockClientException.class, RestClientException.class})
    public ProblemDetail handleSourceUnavailable(RuntimeException e) {
        log.warn("Внешний сервис недоступен", e);
        return problem(ErrorCode.SOURCE_UNAVAILABLE, "Сервис данных временно недоступен, попробуйте позже");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(ErrorCode.VALIDATION_ERROR, detail);
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ProblemDetail handleBadRequest(Exception e) {
        return problem(ErrorCode.VALIDATION_ERROR, "Некорректный запрос: " + e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException e) {
        return problem(ErrorCode.NOT_FOUND, "Адрес не найден: /" + e.getResourcePath());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethod(HttpRequestMethodNotSupportedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage());
        problem.setProperty("code", ErrorCode.VALIDATION_ERROR);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Необработанная ошибка", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Внутренняя ошибка сервера");
        problem.setProperty("code", "INTERNAL_ERROR");
        return problem;
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setProperty("code", code);
        return problem;
    }
}
