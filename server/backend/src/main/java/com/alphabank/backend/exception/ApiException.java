package com.alphabank.backend.exception;

import com.alphabank.backend.enums.ApplicationStatus;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final ApplicationStatus currentStatus;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, null);
    }

    public ApiException(ErrorCode code, String detail, ApplicationStatus currentStatus) {
        super(detail);
        this.code = code;
        this.currentStatus = currentStatus;
    }

    public static ApiException validation(String detail) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, detail);
    }

    public static ApiException invalidTransition(ApplicationStatus from, String action) {
        return new ApiException(ErrorCode.INVALID_TRANSITION,
                "Действие " + action + " недопустимо в статусе " + from, from);
    }

    public ErrorCode code() {
        return code;
    }

    public ApplicationStatus currentStatus() {
        return currentStatus;
    }
}
