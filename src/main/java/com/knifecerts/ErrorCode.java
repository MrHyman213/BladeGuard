package com.knifecerts;

/**
 * Коды ошибок для системы подачи сертификатов.
 * Каждый код содержит уникальный идентификатор и сообщение об ошибке.
 */
public enum ErrorCode {
    YANDEX_DISK_UPLOAD_FAILED("YD001", "Не удалось загрузить файл на Yandex.Disk"),
    YANDEX_DISK_MOVE_FAILED("YD002", "Не удалось переместить файл на Yandex.Disk"),
    YANDEX_DISK_DELETE_FAILED("YD003", "Не удалось удалить файл с Yandex.Disk"),
    DATABASE_ERROR("DB001", "Ошибка базы данных"),
    SUBMISSION_NOT_FOUND("SUB001", "Заявка не найдена"),
    SUBMISSION_ALREADY_MODERATED("SUB002", "Заявка уже проверена"),
    INVALID_SUBMISSION_STATUS("SUB003", "Недопустимый статус заявки"),
    TELEGRAM_API_ERROR("TG001", "Ошибка Telegram API"),
    UNEXPECTED_ERROR("SYS001", "Неожиданная ошибка системы");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
