package com.knifecerts;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionStatus;
import com.knifecerts.repository.SubmissionRepository;

/**
 * Основной сервис бизнес-логики для управления заявками на сертификаты.
 * 
 * Предоставляет методы для создания заявок, получения списков заявок,
 * и модерации (одобрение/отклонение) заявок.
 * 
 * Все операции модерации выполняются в транзакциях для обеспечения
 * согласованности данных между базой данных и Yandex.Disk.
 * 
 * Требование: 5.1
 */
@Service
@Transactional
public class SubmissionService {
    
    private static final Logger logger = Logger.getLogger(SubmissionService.class.getName());
    
    private final SubmissionRepository submissionRepository;
    private final YandexDiskService yandexDiskService;
    
    /**
     * Конструктор с внедрением зависимостей.
     * 
     * @param submissionRepository репозиторий для работы с заявками
     * @param yandexDiskService сервис для работы с Yandex.Disk
     */
    public SubmissionService(SubmissionRepository submissionRepository, 
                           YandexDiskService yandexDiskService) {
        this.submissionRepository = submissionRepository;
        this.yandexDiskService = yandexDiskService;
    }
    
    /**
     * Создает новую заявку на сертификат.
     * 
     * Создает запись заявки в базе данных со статусом PENDING.
     * Все обязательные поля должны быть заполнены, опциональные поля
     * (modelName, description) могут быть null.
     * 
     * @param userId Telegram ID пользователя
     * @param username Telegram username пользователя
     * @param modelName название модели ножа (может быть null)
     * @param description описание сертификата (может быть null)
     * @param photoPath путь к фото на Yandex.Disk
     * @return созданная заявка с установленным ID
     * 
     * Требования: 5.1, 5.2, 5.3, 5.4, 5.5
     */
    public Submission createSubmission(Long userId, String username,
                                      String photoPath, String modelName,
                                      String description, List<String> alternativeModels) {
        logger.info("Creating new submission for user: " + userId);

        Submission submission = new Submission(userId, username, modelName, description, photoPath);
        submission.setAlternativeModelsList(alternativeModels);
        Submission savedSubmission = submissionRepository.save(submission);

        logger.info("Submission created with ID: " + savedSubmission.getId());
        return savedSubmission;
    }

    
    /**
     * Получает список всех заявок со статусом PENDING, отсортированных по дате создания.
     * 
     * Используется модераторами для просмотра очереди ожидающих заявок.
     * Заявки возвращаются в порядке поступления (FIFO).
     * 
     * @return список ожидающих заявок
     * 
     * Требование: 7.1
     */
    public List<Submission> getPendingSubmissions() {
        logger.info("Получение списка ожидающих заявок");
        List<Submission> submissions = submissionRepository.findByStatusOrderByCreatedAtAsc(SubmissionStatus.PENDING);
        logger.info("Найдено ожидающих заявок: " + submissions.size());
        return submissions;
    }
    
    /**
     * Получает заявку по её ID.
     * 
     * Используется для просмотра деталей конкретной заявки модератором.
     * 
     * @param id ID заявки
     * @return Optional с заявкой, если найдена, иначе пустой Optional
     * 
     * Требование: 8.1
     */
    public Optional<Submission> getSubmissionById(Long id) {
        logger.info("Getting submission with ID: " + id);
        return submissionRepository.findById(id);
    }
    
    public List<Submission> getSubmissionsByUserId(Long userId) {
        logger.info("Getting submissions for user: " + userId);
        return submissionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Валидирует, что заявка существует и имеет статус PENDING.
     * 
     * Используется перед операциями модерации для проверки, что заявка
     * может быть одобрена или отклонена.
     * 
     * @param submission заявка для валидации
     * @throws SubmissionException если заявка уже проверена
     * 
     * Требования: 9.1, 9.6, 10.1, 10.6
     */
    private void validatePendingStatus(Submission submission) throws SubmissionException {
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            logger.warning("Попытка модерации уже проверенной заявки: " + submission.getId());
            throw new SubmissionException(
                ErrorCode.SUBMISSION_ALREADY_MODERATED,
                "Заявка #" + submission.getId() + " уже имеет статус " + submission.getStatus()
            );
        }
    }
    
    /**
     * Одобряет заявку на сертификат.
     * 
     * Перемещает фото из папки offers в папку certificates на Yandex.Disk,
     * обновляет статус заявки на APPROVED и устанавливает поля модерации.
     * 
     * Операция выполняется в транзакции. При ошибке перемещения файла
     * изменения в базе данных откатываются.
     * 
     * @param submissionId ID заявки для одобрения
     * @param moderatorId Telegram ID модератора
     * @return одобренная заявка
     * @throws SubmissionException если заявка не найдена или уже проверена
     * 
     * Требования: 9.1, 9.2, 9.3, 9.4, 9.6
     */
    public Submission approveSubmission(Long submissionId, Long moderatorId) 
            throws SubmissionException {
        logger.info("Одобрение заявки #" + submissionId + " модератором " + moderatorId);
        
        // Получаем заявку
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionException(
                    ErrorCode.SUBMISSION_NOT_FOUND,
                    "Заявка #" + submissionId + " не найдена"
                ));
        
        // Валидируем статус
        validatePendingStatus(submission);
        
        // Перемещаем файл из offers в certificates
        String sourcePath = submission.getPhotoPath();
        String fileName = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
        String destinationPath = "app:/certificates/" + fileName;
        
        try {
            yandexDiskService.moveFile(sourcePath, destinationPath);
            logger.info("Файл перемещен: " + sourcePath + " -> " + destinationPath);
        } catch (IOException e) {
            logger.severe("Ошибка перемещения файла при одобрении заявки #" + submissionId);
            throw new SubmissionException(
                ErrorCode.YANDEX_DISK_MOVE_FAILED,
                "Не удалось переместить файл: " + e.getMessage()
            );
        }
        
        // Обновляем заявку
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setPhotoPath(destinationPath);
        submission.setModeratedAt(LocalDateTime.now());
        submission.setModeratedBy(moderatorId);
        
        Submission savedSubmission = submissionRepository.save(submission);
        logger.info("Заявка #" + submissionId + " одобрена");
        
        return savedSubmission;
    }
    
    /**
     * Отклоняет заявку на сертификат.
     * 
     * Удаляет фото из папки offers на Yandex.Disk,
     * обновляет статус заявки на REJECTED и устанавливает поля модерации.
     * 
     * Операция выполняется в транзакции. При ошибке удаления файла
     * изменения в базе данных откатываются.
     * 
     * @param submissionId ID заявки для отклонения
     * @param moderatorId Telegram ID модератора
     * @return отклоненная заявка
     * @throws SubmissionException если заявка не найдена или уже проверена
     * 
     * Требования: 10.1, 10.2, 10.3, 10.4, 10.6
     */
    public Submission rejectSubmission(Long submissionId, Long moderatorId) 
            throws SubmissionException {
        logger.info("Отклонение заявки #" + submissionId + " модератором " + moderatorId);
        
        // Получаем заявку
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionException(
                    ErrorCode.SUBMISSION_NOT_FOUND,
                    "Заявка #" + submissionId + " не найдена"
                ));
        
        // Валидируем статус
        validatePendingStatus(submission);
        
        // Удаляем файл из offers
        String filePath = submission.getPhotoPath();
        
        try {
            yandexDiskService.deleteFile(filePath);
            logger.info("Файл удален: " + filePath);
        } catch (IOException e) {
            logger.severe("Ошибка удаления файла при отклонении заявки #" + submissionId);
            throw new SubmissionException(
                ErrorCode.YANDEX_DISK_DELETE_FAILED,
                "Не удалось удалить файл: " + e.getMessage()
            );
        }
        
        // Обновляем заявку
        submission.setStatus(SubmissionStatus.REJECTED);
        submission.setModeratedAt(LocalDateTime.now());
        submission.setModeratedBy(moderatorId);
        
        Submission savedSubmission = submissionRepository.save(submission);
        logger.info("Заявка #" + submissionId + " отклонена");
        
        return savedSubmission;
    }
    
    /**
     * Обновляет данные заявки.
     * 
     * Используется для редактирования заявки модератором перед одобрением.
     * 
     * @param submissionId ID заявки
     * @param modelName новое название модели (может быть null)
     * @param description новое описание (может быть null)
     * @param alternativeModels новый список альтернативных моделей (может быть null)
     */
    public void updateSubmission(Long submissionId, String modelName, 
                                String description, List<String> alternativeModels) {
        logger.info("Обновление заявки #" + submissionId);
        
        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Заявка #" + submissionId + " не найдена"));
        
        if (modelName != null) {
            submission.setModelName(modelName);
        }
        if (description != null) {
            submission.setDescription(description);
        }
        if (alternativeModels != null) {
            submission.setAlternativeModelsList(alternativeModels);
        }
        
        submissionRepository.save(submission);
        logger.info("Заявка #" + submissionId + " обновлена");
    }
    
    /**
     * Отклоняет заявку с указанием причины.
     * 
     * @param submissionId ID заявки
     * @param moderatorId ID модератора
     * @param reason причина отклонения
     * @return отклоненная заявка
     * @throws SubmissionException если заявка не найдена или уже проверена
     */
    public Submission rejectSubmissionWithReason(Long submissionId, Long moderatorId, String reason) 
            throws SubmissionException {
        Submission submission = rejectSubmission(submissionId, moderatorId);
        submission.setRejectionReason(reason);
        return submissionRepository.save(submission);
    }
    
    public List<Submission> getApprovedSubmissions() {
        logger.info("Getting approved submissions");
        return submissionRepository.findByStatus(SubmissionStatus.APPROVED);
    }
}
