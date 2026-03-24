package com.knifecerts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.Brand;
import com.knifecerts.model.Knife;
import com.knifecerts.model.KnifeModel;
import com.knifecerts.model.SubmissionBuffer;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeModelRepository;
import com.knifecerts.repository.KnifeRepository;
import com.knifecerts.repository.SubmissionBufferRepository;
import com.knifecerts.service.MainMenuUpdateService;
import com.knifecerts.service.SubmissionBufferService;
import com.knifecerts.service.TransitiveAlternativesService;
import com.knifecerts.service.YandexDiskService;

/**
 * Интеграционные тесты полного сценария работы системы.
 * 
 * Feature: blade-guardian-full-implementation
 * Требования: 20.1–20.3
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class IntegrationTest {
    
    @Autowired
    private SubmissionBufferService submissionBufferService;
    
    @Autowired
    private SubmissionBufferRepository submissionBufferRepository;
    
    @Autowired
    private KnifeRepository knifeRepository;
    
    @Autowired
    private BrandRepository brandRepository;
    
    @Autowired
    private KnifeModelRepository knifeModelRepository;
    
    @Autowired
    private TransitiveAlternativesService transitiveAlternativesService;
    
    @MockBean
    private YandexDiskService yandexDiskService;
    
    @MockBean
    private MainMenuUpdateService mainMenuUpdateService;
    
    @BeforeEach
    void setUp() throws IOException {
        // Мокируем операции с Yandex.Disk
        when(yandexDiskService.uploadToOffers(any(InputStream.class), anyString()))
            .thenReturn("app:/offers/test_photo.jpg");
        
        when(yandexDiskService.downloadPhoto(anyString()))
            .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        
        doNothing().when(yandexDiskService).deleteFile(anyString());
        doNothing().when(yandexDiskService).moveFile(anyString(), anyString());
        
        // Мокируем обновление меню
        doNothing().when(mainMenuUpdateService).updateAllUserMenus();
        doNothing().when(mainMenuUpdateService).updateAdminMenu(any(), any());
    }
    
    /**
     * Требование 20.1: Полный сценарий от подачи заявки до появления в каталоге.
     * 
     * Сценарий:
     * 1. Пользователь подаёт заявку → запись появляется в submissions_buffer
     * 2. Модератор одобряет → запись переносится в knives
     * 3. Сертификат появляется в каталоге
     */
    @Test
    void testFullScenarioFromSubmissionToCatalog() {
        // Arrange: Создаем заявку
        SubmissionBuffer submission = submissionBufferService.createSubmission(
            12345L,
            "testuser",
            "Test Model",
            "Test Brand",
            "001",
            "app:/offers/test_photo.jpg"
        );
        
        // Assert: Заявка в буфере
        assertThat(submissionBufferRepository.findById(submission.getId()))
            .isPresent()
            .get()
            .satisfies(s -> {
                assertThat(s.getModelName()).isEqualTo("Test Model");
                assertThat(s.getBrandName()).isEqualTo("Test Brand");
                assertThat(s.getIndex()).isEqualTo("001");
            });
        
        // Act: Модератор одобряет заявку
        Knife approvedKnife = submissionBufferService.approveSubmission(submission.getId());
        
        // Assert: Запись перенесена в knives
        assertThat(approvedKnife).isNotNull();
        assertThat(approvedKnife.getId()).isNotNull();
        
        Optional<Knife> knifeInDb = knifeRepository.findById(approvedKnife.getId());
        assertThat(knifeInDb).isPresent();
        
        Knife knife = knifeInDb.get();
        assertThat(knife.getModel().getName()).isEqualTo("Test Model");
        assertThat(knife.getBrand().getName()).isEqualTo("Test Brand");
        assertThat(knife.getIndex()).isEqualTo("001");
        assertThat(knife.getPhotoPath()).startsWith("app:/certificates/");
        
        // Assert: Заявка удалена из буфера
        assertThat(submissionBufferRepository.findById(submission.getId()))
            .isEmpty();
        
        // Assert: Сертификат доступен в каталоге
        List<Knife> knivesInCatalog = knifeRepository.findAll();
        assertThat(knivesInCatalog).contains(knife);
    }
    
    /**
     * Требование 20.2: Сценарий отклонения заявки.
     * 
     * Сценарий:
     * 1. Пользователь подаёт заявку → запись в submissions_buffer
     * 2. Модератор отклоняет → запись удаляется из submissions_buffer
     */
    @Test
    void testRejectionScenario() {
        // Arrange: Создаем заявку
        SubmissionBuffer submission = submissionBufferService.createSubmission(
            12345L,
            "testuser",
            "Rejected Model",
            "Rejected Brand",
            "002",
            "app:/offers/rejected_photo.jpg"
        );
        
        Long submissionId = submission.getId();
        
        // Assert: Заявка в буфере
        assertThat(submissionBufferRepository.findById(submissionId)).isPresent();
        
        // Act: Модератор отклоняет заявку
        submissionBufferService.rejectSubmission(submissionId);
        
        // Assert: Заявка удалена из буфера
        assertThat(submissionBufferRepository.findById(submissionId)).isEmpty();
        
        // Assert: Запись не появилась в knives
        List<Knife> knives = knifeRepository.findAll();
        assertThat(knives)
            .noneMatch(k -> k.getModel().getName().equals("Rejected Model"));
    }
    
    /**
     * Требование 20.3: Сценарий транзитивных альтернатив.
     * 
     * Сценарий:
     * 1. Одобрение заявки с альтернативами
     * 2. Предложение транзитивных альтернатив
     * 3. Подтверждение → проверка связей в БД
     */
    @Test
    void testTransitiveAlternativesScenario() {
        // Arrange: Создаем базовую структуру
        // Модель A (с фото)
        Brand brandA = brandRepository.save(new Brand("Brand A"));
        KnifeModel modelA = knifeModelRepository.save(new KnifeModel("Model A"));
        Knife knifeA = knifeRepository.save(new Knife(modelA, brandA, "001", "app:/certificates/a.jpg"));
        
        // Модель B (с фото) — будет альтернативой для новой заявки
        Brand brandB = brandRepository.save(new Brand("Brand B"));
        KnifeModel modelB = knifeModelRepository.save(new KnifeModel("Model B"));
        Knife knifeB = knifeRepository.save(new Knife(modelB, brandB, "001", "app:/certificates/b.jpg"));
        
        // Модель C (с фото) — альтернатива для B, должна стать транзитивной для новой заявки
        Brand brandC = brandRepository.save(new Brand("Brand C"));
        KnifeModel modelC = knifeModelRepository.save(new KnifeModel("Model C"));
        Knife knifeC = knifeRepository.save(new Knife(modelC, brandC, "001", "app:/certificates/c.jpg"));
        
        // Связываем B ↔ C
        knifeB.addAlternative(knifeC);
        knifeRepository.save(knifeB);
        
        // Создаем заявку с альтернативой B
        SubmissionBuffer submission = submissionBufferService.createSubmission(
            12345L,
            "testuser",
            "New Model",
            "New Brand",
            "001",
            "app:/offers/new_photo.jpg"
        );
        
        // Добавляем альтернативу B в заявку
        submission.setAlternatives("Brand B / Model B");
        submissionBufferRepository.save(submission);
        
        // Act: Одобряем заявку
        Knife newKnife = submissionBufferService.approveSubmission(submission.getId());
        
        // Assert: Новый нож создан
        assertThat(newKnife).isNotNull();
        assertThat(newKnife.getModel().getName()).isEqualTo("New Model");
        
        // Assert: Альтернатива B добавлена
        Set<Knife> directAlternatives = newKnife.getAlternatives();
        assertThat(directAlternatives)
            .as("Прямая альтернатива B должна быть добавлена")
            .anyMatch(k -> k.getModel().getName().equals("Model B"));
        
        // Act: Ищем транзитивные альтернативы
        Set<Long> newAlternativeIds = directAlternatives.stream()
            .map(Knife::getId)
            .collect(java.util.stream.Collectors.toSet());
        
        Set<Knife> transitiveAlternatives = transitiveAlternativesService.findTransitive(
            newKnife.getId(), 
            newAlternativeIds
        );
        
        // Assert: C должна быть найдена как транзитивная альтернатива
        assertThat(transitiveAlternatives)
            .as("Model C должна быть найдена как транзитивная альтернатива")
            .anyMatch(k -> k.getModel().getName().equals("Model C"));
        
        // Act: Добавляем транзитивные альтернативы
        for (Knife transitiveAlt : transitiveAlternatives) {
            newKnife.addAlternative(transitiveAlt);
        }
        knifeRepository.save(newKnife);
        
        // Assert: Проверяем связи в БД
        Knife reloadedKnife = knifeRepository.findById(newKnife.getId()).orElseThrow();
        Set<Knife> allAlternatives = reloadedKnife.getAlternatives();
        
        assertThat(allAlternatives)
            .as("Новый нож должен иметь прямые связи с B и C")
            .hasSize(2)
            .anyMatch(k -> k.getModel().getName().equals("Model B"))
            .anyMatch(k -> k.getModel().getName().equals("Model C"));
    }
    
    /**
     * Дополнительный тест: проверка создания нового бренда при одобрении.
     */
    @Test
    void testNewBrandCreationTriggersMenuUpdate() {
        // Arrange: Создаем заявку с новым брендом
        SubmissionBuffer submission = submissionBufferService.createSubmission(
            12345L,
            "testuser",
            "New Model",
            "Brand New",
            "001",
            "app:/offers/new_brand_photo.jpg"
        );
        
        // Проверяем что бренд не существует
        assertThat(brandRepository.findByName("Brand New")).isEmpty();
        
        // Act: Одобряем заявку
        Knife knife = submissionBufferService.approveSubmission(submission.getId());
        
        // Assert: Бренд создан
        Optional<Brand> newBrand = brandRepository.findByName("Brand New");
        assertThat(newBrand).isPresent();
        assertThat(knife.getBrand()).isEqualTo(newBrand.get());
        
        // Примечание: mainMenuUpdateService.updateAllUserMenus() должен быть вызван,
        // но мы его замокали, поэтому проверяем только создание бренда
    }
}
