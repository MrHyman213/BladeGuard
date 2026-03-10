package com.knifecerts;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.Brand;
import com.knifecerts.model.Knife;
import com.knifecerts.model.SubmissionBuffer;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class NewSchemaIntegrationTest {

    @Autowired
    private SubmissionBufferService submissionBufferService;

    @Autowired
    private KnifeService knifeService;

    @Test
    public void testSubmissionBufferWorkflow() {
        // Создаем заявку
        SubmissionBuffer submission = submissionBufferService.createSubmission(
            12345L, "testuser", "TestModel", "TestBrand", "IDX001", "/test/path.jpg"
        );
        
        assertNotNull(submission);
        assertNotNull(submission.getId());
        assertEquals("TestModel", submission.getModelName());
        assertEquals("TestBrand", submission.getBrandName());
        assertEquals("IDX001", submission.getIndex());
        
        // Добавляем альтернативу
        submissionBufferService.addAlternativeToSubmission(
            submission.getId(), "AltModel", "AltBrand"
        );
        
        // Проверяем, что альтернатива добавилась
        Optional<SubmissionBuffer> updated = submissionBufferService.getSubmissionById(submission.getId());
        assertTrue(updated.isPresent());
        assertEquals(1, updated.get().getAlternatives().size());
        assertEquals("AltModel", updated.get().getAlternatives().get(0).getModelName());
        assertEquals("AltBrand", updated.get().getAlternatives().get(0).getBrandName());
        
        // Одобряем заявку
        Knife knife = submissionBufferService.approveSubmission(submission.getId());
        
        assertNotNull(knife);
        assertNotNull(knife.getId());
        assertEquals("TestModel", knife.getModel().getName());
        assertEquals("TestBrand", knife.getBrand().getName());
        assertEquals("IDX001", knife.getIndex());
        assertEquals("/test/path.jpg", knife.getPhotoPath());
        
        // Проверяем, что заявка удалилась из буфера
        Optional<SubmissionBuffer> deleted = submissionBufferService.getSubmissionById(submission.getId());
        assertFalse(deleted.isPresent());
        
        // Проверяем, что нож появился в сертификатах
        List<Knife> certificates = knifeService.getAllCertificates();
        assertTrue(certificates.stream().anyMatch(k -> k.getId().equals(knife.getId())));
        
        // Проверяем, что бренд появился в списке брендов с сертификатами
        List<Brand> brands = knifeService.getAllBrandsWithCertificates();
        assertTrue(brands.stream().anyMatch(b -> b.getName().equals("TestBrand")));
    }

    @Test
    public void testRejectSubmission() {
        // Создаем заявку
        SubmissionBuffer submission = submissionBufferService.createSubmission(
            12346L, "testuser2", "RejectModel", "RejectBrand", null, "/reject/path.jpg"
        );
        
        Long submissionId = submission.getId();
        
        // Отклоняем заявку
        submissionBufferService.rejectSubmission(submissionId);
        
        // Проверяем, что заявка удалилась
        Optional<SubmissionBuffer> deleted = submissionBufferService.getSubmissionById(submissionId);
        assertFalse(deleted.isPresent());
        
        // Проверяем, что нож не создался
        List<Knife> certificates = knifeService.getAllCertificates();
        assertFalse(certificates.stream().anyMatch(k -> 
            k.getModel().getName().equals("RejectModel") && 
            k.getBrand().getName().equals("RejectBrand")
        ));
    }

    @Test
    public void testKnifeServiceMethods() {
        // Создаем и одобряем заявку для тестирования
        SubmissionBuffer submission = submissionBufferService.createSubmission(
            12347L, "testuser3", "ServiceTestModel", "ServiceTestBrand", "SRV001", "/service/path.jpg"
        );
        
        Knife knife = submissionBufferService.approveSubmission(submission.getId());
        
        // Тестируем методы KnifeService
        List<Brand> brands = knifeService.getAllBrandsWithCertificates();
        assertTrue(brands.stream().anyMatch(b -> b.getName().equals("ServiceTestBrand")));
        
        List<Knife> brandKnives = knifeService.getCertificatesByBrand("ServiceTestBrand");
        assertTrue(brandKnives.stream().anyMatch(k -> k.getId().equals(knife.getId())));
        
        Optional<Knife> foundKnife = knifeService.getKnifeById(knife.getId());
        assertTrue(foundKnife.isPresent());
        assertEquals("ServiceTestModel", foundKnife.get().getModel().getName());
        
        // Тестируем поиск
        List<Knife> searchResults = knifeService.searchKnives("ServiceTest");
        assertTrue(searchResults.stream().anyMatch(k -> k.getId().equals(knife.getId())));
    }
}