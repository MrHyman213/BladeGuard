package com.knifecerts.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.dto.AlternativeEntry;
import com.knifecerts.model.Brand;
import com.knifecerts.model.Knife;
import com.knifecerts.model.KnifeModel;
import com.knifecerts.model.SubmissionBuffer;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeModelRepository;
import com.knifecerts.repository.KnifeRepository;
import com.knifecerts.repository.SubmissionBufferRepository;

@Service
@Transactional
public class SubmissionBufferService {

    private final SubmissionBufferRepository submissionBufferRepository;
    private final BrandRepository brandRepository;
    private final KnifeModelRepository knifeModelRepository;
    private final KnifeRepository knifeRepository;
    private final YandexDiskService yandexDiskService;
    private final MainMenuUpdateService mainMenuUpdateService;

    @Autowired
    public SubmissionBufferService(
            SubmissionBufferRepository submissionBufferRepository,
            BrandRepository brandRepository,
            KnifeModelRepository knifeModelRepository,
            KnifeRepository knifeRepository,
            YandexDiskService yandexDiskService,
            MainMenuUpdateService mainMenuUpdateService) {
        this.submissionBufferRepository = submissionBufferRepository;
        this.brandRepository = brandRepository;
        this.knifeModelRepository = knifeModelRepository;
        this.knifeRepository = knifeRepository;
        this.yandexDiskService = yandexDiskService;
        this.mainMenuUpdateService = mainMenuUpdateService;
    }

    public SubmissionBuffer createSubmission(Long userId, String username, String modelName, 
                                           String brandName, String index, String photoPath) {
        SubmissionBuffer submission = new SubmissionBuffer(userId, username, modelName, brandName, photoPath);
        submission.setIndex(index);
        return submissionBufferRepository.save(submission);
    }

    public List<SubmissionBuffer> getAllPendingSubmissions() {
        return submissionBufferRepository.findAllOrderByCreatedAt();
    }

    public Optional<SubmissionBuffer> getSubmissionById(Long id) {
        return submissionBufferRepository.findById(id);
    }

    public List<SubmissionBuffer> getSubmissionsByUserId(Long userId) {
        return submissionBufferRepository.findByUserId(userId);
    }

    public void addAlternativeToSubmission(Long submissionId, String modelName, String brandName) {
        Optional<SubmissionBuffer> submissionOpt = submissionBufferRepository.findById(submissionId);
        if (submissionOpt.isPresent()) {
            SubmissionBuffer submission = submissionOpt.get();
            // Требование 2.8: Нормализация пробелов - всегда "Бренд / Название"
            String altEntry = brandName != null && !brandName.isEmpty() 
                ? brandName + " / " + modelName 
                : modelName;
            
            String current = submission.getAlternatives();
            if (current == null || current.isEmpty()) {
                submission.setAlternatives(altEntry);
            } else {
                submission.setAlternatives(current + ", " + altEntry);
            }
            submissionBufferRepository.save(submission);
        }
    }

    public void removeAlternativeFromSubmission(Long submissionId, String modelName, String brandName) {
        Optional<SubmissionBuffer> submissionOpt = submissionBufferRepository.findById(submissionId);
        if (submissionOpt.isPresent()) {
            SubmissionBuffer submission = submissionOpt.get();
            String current = submission.getAlternatives();
            if (current != null && !current.isEmpty()) {
                String[] parts = current.split(",");
                StringBuilder updated = new StringBuilder();
                for (String part : parts) {
                    String trimmed = part.trim();
                    String[] altParts = trimmed.split("/");
                    boolean matches = false;
                    if (altParts.length == 2) {
                        matches = altParts[0].trim().equals(brandName) && altParts[1].trim().equals(modelName);
                    } else if (altParts.length == 1) {
                        matches = altParts[0].trim().equals(modelName) && (brandName == null || brandName.isEmpty());
                    }
                    if (!matches) {
                        if (updated.length() > 0) updated.append(", ");
                        updated.append(trimmed);
                    }
                }
                submission.setAlternatives(updated.length() > 0 ? updated.toString() : null);
                submissionBufferRepository.save(submission);
            }
        }
    }

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Генерирует уникальное имя файла для сертификата на основе оригинального пути.
     */
    private String generateCertificateFileName(String originalPath) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        // Берём оригинальное имя файла как суффикс для читаемости
        String originalName = originalPath.substring(originalPath.lastIndexOf('/') + 1);
        return "cert_" + timestamp + "_" + originalName;
    }

    /**
     * Копирует файл из app:/offers/ в app:/certificates/ и возвращает новый путь.
     * Операция: скачать из offers → загрузить в certificates с новым именем.
     */
    private String copyOfferToCertificates(String offersPath) throws IOException {
        String newFileName = generateCertificateFileName(offersPath);
        try (InputStream stream = yandexDiskService.downloadPhoto(offersPath)) {
            return yandexDiskService.uploadToCertificates(stream, newFileName);
        }
    }

    @Transactional
    public Knife approveSubmission(Long submissionId) {
        Optional<SubmissionBuffer> submissionOpt = submissionBufferRepository.findById(submissionId);
        if (!submissionOpt.isPresent()) {
            throw new RuntimeException("Submission not found: " + submissionId);
        }

        SubmissionBuffer submission = submissionOpt.get();

        // Шаг 1: Копируем фото из app:/offers/ в app:/certificates/
        String offersPath = submission.getPhotoPath();
        String certificatesPath;
        try {
            certificatesPath = copyOfferToCertificates(offersPath);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось скопировать фото в certificates: " + e.getMessage(), e);
        }

        // Создаем или находим бренд и модель
        BrandWithCreated brandResult = findOrCreateBrand(submission.getBrandName());
        KnifeModel model = findOrCreateKnifeModel(submission.getModelName());

        // Шаг 2: Создаем запись в knives с НОВЫМ путём в app:/certificates/
        // Компенсирующая операция: если шаг 2 или 3 упадёт — удалить скопированный файл
        Knife knife;
        try {
            knife = new Knife(model, brandResult.brand, submission.getIndex(), certificatesPath);
            knife = knifeRepository.save(knife);
        } catch (Exception e) {
            // Компенсация: удаляем скопированный файл из certificates
            tryDeleteFile(certificatesPath);
            throw new RuntimeException("Не удалось создать запись ножа в БД: " + e.getMessage(), e);
        }

        // Шаг 3: Удаляем оригинал из app:/offers/
        try {
            yandexDiskService.deleteFile(offersPath);
        } catch (IOException e) {
            // Компенсация: удаляем скопированный файл из certificates
            tryDeleteFile(certificatesPath);
            throw new RuntimeException("Не удалось удалить оригинал из offers: " + e.getMessage(), e);
        }

        // Обрабатываем альтернативы из TEXT поля
        String alternativesStr = submission.getAlternatives();
        if (alternativesStr != null && !alternativesStr.isEmpty()) {
            // Используем разделитель с пробелами для парсинга
            AlternativesParser parser = new AlternativesParserImpl(" / ");
            List<AlternativeEntry> alternatives = parser.parse(alternativesStr);

            for (AlternativeEntry altEntry : alternatives) {
                BrandWithCreated altBrandResult = findOrCreateBrand(altEntry.brand());
                KnifeModel altModel = findOrCreateKnifeModel(altEntry.name());

                Optional<Knife> existingKnife = knifeRepository.findByModelAndBrand(altModel, altBrandResult.brand);
                Knife alternativeKnife;

                if (existingKnife.isPresent()) {
                    alternativeKnife = existingKnife.get();
                } else {
                    alternativeKnife = new Knife(altModel, altBrandResult.brand, null, null);
                    alternativeKnife = knifeRepository.save(alternativeKnife);
                }

                knife.addAlternative(alternativeKnife);
            }
        }

        knifeRepository.save(knife);

        // Шаг 4: Удаляем заявку из буфера
        submissionBufferRepository.delete(submission);

        // Если был создан новый бренд, обновляем меню у всех пользователей (Req 6.1, 6.5, 6.6)
        if (brandResult.created) {
            mainMenuUpdateService.updateAllUserMenus();
        }

        return knife;
    }

    /**
     * Пытается удалить файл с Yandex.Disk как компенсирующую операцию.
     * Ошибки логируются, но не пробрасываются — компенсация не должна маскировать исходную ошибку.
     */
    private void tryDeleteFile(String path) {
        try {
            yandexDiskService.deleteFile(path);
        } catch (IOException ex) {
            // Логируем, но не пробрасываем — это компенсирующая операция
            System.err.println("Компенсация: не удалось удалить файл " + path + ": " + ex.getMessage());
        }
    }

    public void rejectSubmission(Long submissionId) {
        submissionBufferRepository.deleteById(submissionId);
    }

    /**
     * Проверяет наличие существующего ножа с совпадающими brand+name+index
     * 
     * @param brandName название бренда
     * @param modelName название модели
     * @param index индекс ножа
     * @return Optional с существующим ножом, если найден
     */
    public Optional<Knife> findDuplicateKnife(String brandName, String modelName, String index) {
        // Нормализуем значения
        String normalizedBrand = brandName != null ? brandName.trim() : "";
        String normalizedModel = modelName != null ? modelName.trim() : "";
        String normalizedIndex = index != null ? index.trim() : "";
        
        return knifeRepository.findByModelNameBrandNameAndIndex(normalizedModel, normalizedBrand, normalizedIndex);
    }

    /**
     * Заменяет фото существующего ножа, архивируя старое фото
     * 
     * @param knifeId ID ножа для обновления
     * @param newPhotoPath путь к новому фото
     * @throws IOException если операция с файлами не удалась
     */
    @Transactional
    public void replaceKnifePhoto(Long knifeId, String newPhotoPath) throws IOException {
        Optional<Knife> knifeOpt = knifeRepository.findById(knifeId);
        if (!knifeOpt.isPresent()) {
            throw new RuntimeException("Knife not found: " + knifeId);
        }
        
        Knife knife = knifeOpt.get();
        String oldPhotoPath = knife.getPhotoPath();
        
        // Если у ножа было старое фото, архивируем его
        if (oldPhotoPath != null && !oldPhotoPath.isEmpty()) {
            try {
                // Генерируем путь в архиве: app:/archive/replaced/photo_<timestamp>_<random>.jpg
                String archivePath = generateArchivePath(oldPhotoPath);
                
                // Перемещаем старое фото в архив
                yandexDiskService.moveFile(oldPhotoPath, archivePath);
            } catch (IOException e) {
                // Логируем ошибку, но продолжаем - новое фото все равно будет установлено
                throw new IOException("Не удалось архивировать старое фото: " + e.getMessage(), e);
            }
        }
        
        // Устанавливаем новое фото
        knife.setPhotoPath(newPhotoPath);
        knifeRepository.save(knife);
    }

    /**
     * Генерирует путь в архиве для старого фото с временной меткой для избежания коллизий.
     * Формат: app:/archive/replaced/<timestamp>_<original_filename>
     * 
     * @param originalPath оригинальный путь к фото
     * @return путь в архиве
     */
    private String generateArchivePath(String originalPath) {
        // Извлекаем имя файла из оригинального пути
        String fileName = originalPath.substring(originalPath.lastIndexOf('/') + 1);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        return "app:/archive/replaced/" + timestamp + "_" + fileName;
    }

    /**
     * Находит или создает бренд. Возвращает объект с брендом и флагом, был ли он создан.
     * 
     * @param name название бренда
     * @return BrandWithCreated объект с брендом и флагом created
     */
    private BrandWithCreated findOrCreateBrand(String name) {
        // Если бренд не указан, используем бренд с id = 1 (Ножи без бренда)
        if (name == null || name.trim().isEmpty()) {
            Brand brand = brandRepository.findById(1L)
                    .orElseGet(() -> brandRepository.save(new Brand(null)));
            return new BrandWithCreated(brand, false);
        }
        
        String trimmedName = name.trim();
        return brandRepository.findByName(trimmedName)
                .map(b -> new BrandWithCreated(b, false))
                .orElseGet(() -> {
                    Brand newBrand = brandRepository.save(new Brand(trimmedName));
                    return new BrandWithCreated(newBrand, true);
                });
    }

    /**
     * Вспомогательный класс для возврата бренда и флага создания.
     */
    private static class BrandWithCreated {
        final Brand brand;
        final boolean created;
        
        BrandWithCreated(Brand brand, boolean created) {
            this.brand = brand;
            this.created = created;
        }
    }

    private KnifeModel findOrCreateKnifeModel(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя модели не может быть пустым");
        }
        
        String trimmedName = name.trim();
        return knifeModelRepository.findByName(trimmedName)
                .orElseGet(() -> knifeModelRepository.save(new KnifeModel(trimmedName)));
    }
}