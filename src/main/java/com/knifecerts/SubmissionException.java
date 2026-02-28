package com.knifecerts;

/**
 * Пользовательское исключение для ошибок системы подачи сертификатов.
 * Содержит код ошибки и дополнительные детали для диагностики.
 */
public class SubmissionException extends Exception {
    private final ErrorCode errorCode;
    private final String details;

    public SubmissionException(ErrorCode errorCode, String details) {
        super(errorCode.getMessage() + ": " + details);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }
}
