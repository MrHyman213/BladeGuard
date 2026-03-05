package com.knifecerts;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.dto.SearchResult;
import com.knifecerts.model.Brand;
import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionStatus;
import com.knifecerts.repository.BrandRepository;
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
    private final BrandRepository brandRepository;
    
    /**
     * Конструктор с внедрением зависимостей.
     * 
     * @param submissionRepository репозиторий для работы с заявками
     * @param yandexDiskService сервис для работы с Yandex.Disk
     * @param brandRepository репозиторий для работы с брендами
     */
    public SubmissionService(SubmissionRepository submissionRepository, 
                           YandexDiskService yandexDiskService,
                           BrandRepository brandRepository) {
        this.submissionRepository = submissionRepository;
        this.yandexDiskService = yandexDiskService;
        this.brandRepository = brandRepository;
    }
    
    /**
     * Создает новую заявку на сертификат.
     * 
     * Создает запись заявки в базе данных со статусом PENDING.
     * Все обязательные поля должны быть заполнены, опциональные поля
     * (name, brand, indexCode) могут быть null.
     * 
     * @param userId Telegram ID пользователя
     * @param username Telegram username пользователя
     * @param name название ножа (может быть null)
     * @param brand бренд (может быть null)
     * @param photoPath путь к фото на Yandex.Disk
     * @return созданная заявка с установленным ID
     * 
     * Требования: 5.1, 5.2, 5.3, 5.4, 5.5
     */
    public Submission createSubmission(Long userId, String username,
                                      String photoPath, String name,
                                      String brandName, String indexCode, List<String> alternativeModels) {
        logger.info("Creating new submission for user: " + userId);

        Brand brand = findOrCreateBrand(brandName);
        Submission submission = new Submission(userId, username, name, brand, photoPath);
        submission.setIndexCode(indexCode);
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
        logger.info("DEBUG: sourcePath from DB = " + sourcePath);
        String fileName = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
        String destinationPath = "app:/certificates/" + fileName;
        logger.info("DEBUG: destinationPath = " + destinationPath);
        
        try {
            yandexDiskService.moveFile(sourcePath, destinationPath);
            logger.info("Файл перемещен: " + sourcePath + " -> " + destinationPath);
        } catch (IOException e) {
            logger.severe("Ошибка перемещения файла при одобрении заявки #" + submissionId);
            logger.severe("DEBUG: Exception details: " + e.getClass().getName() + ": " + e.getMessage());
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
        
        // Создаем связи с альтернативными сертификатами
        createAlternativeLinks(savedSubmission);
        
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
     * @param name новое название (может быть null)
     * @param brand новый бренд (может быть null)
     * @param indexCode новый индекс (может быть null)
     * @param alternativeModels новый список альтернативных моделей (может быть null)
     */
    public void updateSubmission(Long submissionId, String name, String brandName, 
                                String indexCode, List<String> alternativeModels) {
        logger.info("Обновление заявки #" + submissionId);
        
        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Заявка #" + submissionId + " не найдена"));
        
        if (name != null) {
            submission.setName(name);
        }
        if (brandName != null) {
            Brand brand = findOrCreateBrand(brandName);
            submission.setBrand(brand);
        }
        if (indexCode != null) {
            submission.setIndexCode(indexCode);
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
     * @param reason причина отклонения (не сохраняется в БД)
     * @return отклоненная заявка
     * @throws SubmissionException если заявка не найдена или уже проверена
     */
    public Submission rejectSubmissionWithReason(Long submissionId, Long moderatorId, String reason) 
            throws SubmissionException {
        // Причина отклонения больше не сохраняется в БД
        return rejectSubmission(submissionId, moderatorId);
    }
    
    public List<Submission> getApprovedSubmissions() {
        logger.info("Getting approved submissions");
        return submissionRepository.findByStatus(SubmissionStatus.APPROVED);
    }
    
    /**
     * Поиск одобренных сертификатов по названию или индексу.
     * 
     * @param query поисковый запрос
     * @return список найденных сертификатов
     */
    public List<Submission> searchApprovedSubmissions(String query) {
        logger.info("Searching approved submissions with query: " + query);
        return submissionRepository.findByStatusAndNameContainingIgnoreCaseOrStatusAndIndexCodeContainingIgnoreCase(
            SubmissionStatus.APPROVED, query, SubmissionStatus.APPROVED, query);
    }
    
    /**
     * Получает все заявки из базы данных.
     * 
     * @return список всех заявок
     */
    public List<Submission> getAllSubmissions() {
        logger.info("Getting all submissions");
        return submissionRepository.findAll();
    }
    
    /**
     * Удаляет все заявки из базы данных.
     * 
     * Используется для полной очистки системы.
     */
    public void deleteAllSubmissions() {
        logger.info("Deleting all submissions from database");
        submissionRepository.deleteAll();
        logger.info("All submissions deleted");
    }
    
    /**
     * Удаляет конкретную заявку из базы данных.
     * 
     * @param submissionId ID заявки для удаления
     */
    public void deleteSubmission(Long submissionId) {
        logger.info("Deleting submission #" + submissionId);
        submissionRepository.deleteById(submissionId);
        logger.info("Submission #" + submissionId + " deleted");
    }
    
    /**
     * Находит или создает бренд по названию.
     * 
     * @param name название бренда (может быть null)
     * @return бренд
     */
    private Brand findOrCreateBrand(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "Бренд не указан";
        }
        
        String normalizedName = name.trim();
        Optional<Brand> existing = brandRepository.findByName(normalizedName);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        Brand newBrand = new Brand();
        newBrand.setName(normalizedName);
        return brandRepository.save(newBrand);
    }
    
    /**
     * Расширенный поиск сертификатов по названию ножа с детальной информацией.
     * 
     * @param query поисковый запрос (название ножа)
     * @return SearchResult с информацией о типе совпадения
     */
    public SearchResult searchCertificatesByKnifeNameDetailed(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new SearchResult(new ArrayList<>(), false);
        }
        
        logger.info("Searching certificates by knife name: " + query);
        
        List<Submission> results = submissionRepository.findByStatusAndNameContainingIgnoreCaseOrStatusAndIndexCodeContainingIgnoreCase(
            SubmissionStatus.APPROVED, query, SubmissionStatus.APPROVED, query);
        
        return new SearchResult(results, !results.isEmpty());
    }
    
    /**
     * Расширенный поиск сертификатов по названию ножа.
     * 
     * @param query поисковый запрос (название ножа)
     * @return список найденных сертификатов
     */
    public List<Submission> searchCertificatesByKnifeName(String query) {
        return searchCertificatesByKnifeNameDetailed(query).getSubmissions();
    }
    
    /**
     * Получает все уникальные бренды из одобренных сертификатов.
     * 
     * @return список уникальных брендов, отсортированный по алфавиту
     */
    public List<String> getAllApprovedBrands() {
        logger.info("Getting all approved brands");
        
        List<Submission> approved = submissionRepository.findByStatus(SubmissionStatus.APPROVED);
        
        java.util.Set<String> uniqueBrands = new java.util.HashSet<>();
        
        for (Submission submission : approved) {
            if (submission.getBrand() != null && submission.getBrand().getName() != null) {
                uniqueBrands.add(submission.getBrand().getName());
            }
        }
        
        List<String> sortedBrands = new ArrayList<>(uniqueBrands);
        java.util.Collections.sort(sortedBrands);
        
        logger.info("Found " + sortedBrands.size() + " unique brands");
        return sortedBrands;
    }
    
    /**
     * Получает все уникальные названия ножей конкретного бренда из одобренных сертификатов.
     * 
     * @param brandName название бренда
     * @return список уникальных названий ножей этого бренда
     */
    public List<String> getApprovedKnifeNamesByBrand(String brandName) {
        logger.info("Getting approved knife names for brand: " + brandName);
        
        List<Submission> approved = submissionRepository.findByStatus(SubmissionStatus.APPROVED).stream()
            .filter(s -> s.getBrand() != null && brandName.equals(s.getBrand().getName()))
            .collect(java.util.stream.Collectors.toList());
        
        java.util.Set<String> uniqueNames = new java.util.HashSet<>();
        
        for (Submission submission : approved) {
            if (submission.getName() != null && !submission.getName().trim().isEmpty()) {
                uniqueNames.add(submission.getName().trim());
            }
            
            List<String> altModels = submission.getAlternativeModelsList();
            for (String altModel : altModels) {
                if (altModel != null && !altModel.trim().isEmpty()) {
                    uniqueNames.add(altModel.trim());
                }
            }
        }
        
        List<String> sortedNames = new ArrayList<>(uniqueNames);
        java.util.Collections.sort(sortedNames);
        
        logger.info("Found " + sortedNames.size() + " unique knife names for brand: " + brandName);
        return sortedNames;
    }
    
    /**
     * Получает все уникальные названия ножей из одобренных сертификатов.
     * Включает как основные названия, так и альтернативные модели.
     * 
     * @return список уникальных названий ножей
     */
    public List<String> getAllApprovedKnifeNames() {
        logger.info("Getting all approved knife names");
        
        // Получаем все одобренные сертификаты
        List<Submission> approved = submissionRepository.findByStatus(SubmissionStatus.APPROVED);
        
        // Собираем все уникальные названия
        java.util.Set<String> uniqueNames = new java.util.HashSet<>();
        
        for (Submission submission : approved) {
            // Добавляем основное название
            if (submission.getName() != null && !submission.getName().trim().isEmpty()) {
                uniqueNames.add(submission.getName().trim());
            }
            
            // Добавляем альтернативные модели
            List<String> altModels = submission.getAlternativeModelsList();
            for (String altModel : altModels) {
                if (altModel != null && !altModel.trim().isEmpty()) {
                    uniqueNames.add(altModel.trim());
                }
            }
        }
        
        // Сортируем по алфавиту
        List<String> sortedNames = new ArrayList<>(uniqueNames);
        java.util.Collections.sort(sortedNames);
        
        logger.info("Found " + sortedNames.size() + " unique knife names");
        return sortedNames;
    }
    
    /**
     * Создает связи между сертификатом и альтернативными сертификатами.
     * Ищет одобренные сертификаты по названиям из альтернативных моделей.
     * 
     * @param submission одобренный сертификат
     */
    private void createAlternativeLinks(Submission submission) {
        List<String> alternativeModels = submission.getAlternativeModelsList();
        if (alternativeModels == null || alternativeModels.isEmpty()) {
            return;
        }
        
        logger.info("Создание связей с альтернативами для сертификата #" + submission.getId());
        
        for (String altModelName : alternativeModels) {
            if (altModelName == null || altModelName.trim().isEmpty()) {
                continue;
            }
            
            List<Submission> foundCertificates = submissionRepository
                .findByStatusAndNameContainingIgnoreCaseOrStatusAndIndexCodeContainingIgnoreCase(
                    SubmissionStatus.APPROVED, altModelName.trim(), 
                    SubmissionStatus.APPROVED, altModelName.trim());
            
            for (Submission altCertificate : foundCertificates) {
                if (!altCertificate.getId().equals(submission.getId())) {
                    submission.addAlternative(altCertificate);
                    logger.info("Связь создана: сертификат #" + submission.getId() + 
                               " <-> сертификат #" + altCertificate.getId());
                }
            }
        }
        
        submissionRepository.save(submission);
    }
    
    public List<Submission> getKnifesByBrand(Brand brand) {
        logger.info("Getting knives for brand: " + brand.getName());
        return submissionRepository.findByStatusAndBrand(SubmissionStatus.APPROVED, brand);
    }
    
    public List<Submission> searchKnivesInBrand(Brand brand, String query) {
        logger.info("Searching knives in brand " + brand.getName() + " with query: " + query);
        List<Submission> allKnives = getKnifesByBrand(brand);
        
        if (query == null || query.trim().isEmpty()) {
            return allKnives;
        }
        
        String lowerQuery = query.toLowerCase();
        return allKnives.stream()
            .filter(s -> (s.getName() != null && s.getName().toLowerCase().contains(lowerQuery)) ||
                        (s.getIndexCode() != null && s.getIndexCode().toLowerCase().contains(lowerQuery)))
            .collect(java.util.stream.Collectors.toList());
    }
    
    public List<Submission> getAlternatives(Long submissionId) {
        logger.info("Getting alternatives for submission #" + submissionId);
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            return List.of();
        }
        
        // Используем ManyToMany relationship
        return new ArrayList<>(submission.getAlternatives());
    }
    
    public void addAlternative(Long submissionId, Long alternativeId) {
        logger.info("Adding alternative: submission #" + submissionId + " <-> #" + alternativeId);
        
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        Submission alternative = submissionRepository.findById(alternativeId).orElse(null);
        
        if (submission == null || alternative == null) {
            logger.warning("Submission or alternative not found");
            return;
        }
        
        // Используем ManyToMany relationship
        submission.addAlternative(alternative);
        submissionRepository.save(submission);
        
        logger.info("Alternative added");
    }
    
    public void removeAlternative(Long submissionId, Long alternativeId) {
        logger.info("Removing alternative: submission #" + submissionId + " <-> #" + alternativeId);
        
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        Submission alternative = submissionRepository.findById(alternativeId).orElse(null);
        
        if (submission == null || alternative == null) {
            logger.warning("Submission or alternative not found");
            return;
        }
        
        // Используем ManyToMany relationship
        submission.removeAlternative(alternative);
        submissionRepository.save(submission);
        
        logger.info("Alternative removed");
    }
    
    public List<Submission> getTransitiveAlternatives(Long submissionId) {
        logger.info("Getting transitive alternatives for submission #" + submissionId);
        
        List<Submission> directAlternatives = getAlternatives(submissionId);
        java.util.Set<Long> allAlternativeIds = new java.util.HashSet<>();
        
        for (Submission alt : directAlternatives) {
            allAlternativeIds.add(alt.getId());
            List<Submission> transitiveAlts = getAlternatives(alt.getId());
            for (Submission transAlt : transitiveAlts) {
                if (!transAlt.getId().equals(submissionId)) {
                    allAlternativeIds.add(transAlt.getId());
                }
            }
        }
        
        allAlternativeIds.remove(submissionId);
        
        return allAlternativeIds.stream()
            .map(id -> submissionRepository.findById(id).orElse(null))
            .filter(s -> s != null)
            .collect(java.util.stream.Collectors.toList());
    }
}
