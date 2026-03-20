package com.knifecerts.service;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.Brand;
import com.knifecerts.model.Knife;
import com.knifecerts.model.KnifeModel;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeModelRepository;
import com.knifecerts.repository.KnifeRepository;

/**
 * Property-based и unit тесты для TransitiveAlternativesService
 * Feature: blade-guardian-full-implementation
 */
@SpringBootTest
@Transactional
public class TransitiveAlternativesServiceTest {
    
    @Autowired
    private TransitiveAlternativesService transitiveAlternativesService;
    
    @Autowired
    private KnifeRepository knifeRepository;
    
    @Autowired
    private KnifeModelRepository knifeModelRepository;
    
    @Autowired
    private BrandRepository brandRepository;
    
    private Brand brand1;
    private Brand brand2;
    private KnifeModel model1;
    private KnifeModel model2;
    private KnifeModel model3;
    private KnifeModel model4;
    private Knife knife1;
    private Knife knife2;
    private Knife knife3;
    private Knife knife4;
    
    @BeforeEach
    public void setUp() {
        // Создаем бренды
        brand1 = new Brand("Brand1");
        brand2 = new Brand("Brand2");
        brandRepository.save(brand1);
        brandRepository.save(brand2);
        
        // Создаем модели
        model1 = new KnifeModel("Model1");
        model2 = new KnifeModel("Model2");
        model3 = new KnifeModel("Model3");
        model4 = new KnifeModel("Model4");
        knifeModelRepository.save(model1);
        knifeModelRepository.save(model2);
        knifeModelRepository.save(model3);
        knifeModelRepository.save(model4);
        
        // Создаем ножи
        knife1 = new Knife();
        knife1.setModel(model1);
        knife1.setBrand(brand1);
        knife1.setPhotoPath("app:/certificates/knife1.jpg");
        
        knife2 = new Knife();
        knife2.setModel(model2);
        knife2.setBrand(brand1);
        knife2.setPhotoPath("app:/certificates/knife2.jpg");
        
        knife3 = new Knife();
        knife3.setModel(model3);
        knife3.setBrand(brand2);
        knife3.setPhotoPath("app:/certificates/knife3.jpg");
        
        knife4 = new Knife();
        knife4.setModel(model4);
        knife4.setBrand(brand2);
        knife4.setPhotoPath("app:/certificates/knife4.jpg");
        
        knifeRepository.save(knife1);
        knifeRepository.save(knife2);
        knifeRepository.save(knife3);
        knifeRepository.save(knife4);
    }
    
    /**
     * Property 4: Транзитивность первого уровня
     * **Validates: Requirements 9.6, 17.1**
     * 
     * Если нож A является альтернативой ножа B, то findTransitive(A, {B})
     * должен содержать все альтернативы B, кроме A.
     */
    @Test
    public void testTransitiveAlternativesFirstLevel() {
        // Создаем граф: knife1 ↔ knife2, knife2 ↔ knife3
        knife1.getAlternatives().add(knife2);
        knife2.getAlternatives().add(knife1);
        knife2.getAlternatives().add(knife3);
        knife3.getAlternatives().add(knife2);
        
        knifeRepository.save(knife1);
        knifeRepository.save(knife2);
        knifeRepository.save(knife3);
        
        // Ищем транзитивные альтернативы для knife1 с добавлением knife2
        Set<Long> newAlternatives = new HashSet<>();
        newAlternatives.add(knife2.getId());
        
        Set<Knife> transitive = transitiveAlternativesService.findTransitive(knife1.getId(), newAlternatives);
        
        // knife1 → knife2 → knife3, поэтому knife3 должна быть в транзитивных
        assertThat(transitive).extracting(Knife::getId).contains(knife3.getId());
    }
    
    /**
     * Property 5: Отсутствие рефлексии в транзитивных альтернативах
     * **Validates: Requirements 17.2**
     * 
     * findTransitive(A, ...) не должен содержать саму модель A.
     */
    @Test
    public void testTransitiveAlternativesNoReflexion() {
        // Создаем граф: knife1 ↔ knife2 ↔ knife3
        knife1.getAlternatives().add(knife2);
        knife2.getAlternatives().add(knife1);
        knife2.getAlternatives().add(knife3);
        knife3.getAlternatives().add(knife2);
        
        knifeRepository.save(knife1);
        knifeRepository.save(knife2);
        knifeRepository.save(knife3);
        
        Set<Long> newAlternatives = new HashSet<>();
        newAlternatives.add(knife2.getId());
        
        Set<Knife> transitive = transitiveAlternativesService.findTransitive(knife1.getId(), newAlternatives);
        
        // knife1 не должна быть в своих транзитивных альтернативах
        assertThat(transitive).extracting(Knife::getId).doesNotContain(knife1.getId());
    }
    
    /**
     * Property 6: Транзитивные альтернативы не пересекаются с прямыми
     * **Validates: Requirements 17.3**
     * 
     * findTransitive(A, direct) не должен содержать ни одной модели из direct.
     */
    @Test
    public void testTransitiveAlternativesNoDirectOverlap() {
        // Создаем граф: knife1 ↔ knife2 ↔ knife3
        knife1.getAlternatives().add(knife2);
        knife2.getAlternatives().add(knife1);
        knife2.getAlternatives().add(knife3);
        knife3.getAlternatives().add(knife2);
        
        knifeRepository.save(knife1);
        knifeRepository.save(knife2);
        knifeRepository.save(knife3);
        
        Set<Long> newAlternatives = new HashSet<>();
        newAlternatives.add(knife2.getId());
        
        Set<Knife> transitive = transitiveAlternativesService.findTransitive(knife1.getId(), newAlternatives);
        
        // knife2 не должна быть в транзитивных (она прямая альтернатива)
        assertThat(transitive).extracting(Knife::getId).doesNotContain(knife2.getId());
    }
    
    /**
     * Unit тест: Инвариант границ
     * **Validates: Requirements 17.4**
     * 
     * Результат findTransitive является подмножеством всех моделей в системе.
     */
    @Test
    public void testTransitiveAlternativesSubsetOfAllModels() {
        // Создаем граф: knife1 ↔ knife2 ↔ knife3
        knife1.getAlternatives().add(knife2);
        knife2.getAlternatives().add(knife1);
        knife2.getAlternatives().add(knife3);
        knife3.getAlternatives().add(knife2);
        
        knifeRepository.save(knife1);
        knifeRepository.save(knife2);
        knifeRepository.save(knife3);
        
        Set<Long> newAlternatives = new HashSet<>();
        newAlternatives.add(knife2.getId());
        
        Set<Knife> transitive = transitiveAlternativesService.findTransitive(knife1.getId(), newAlternatives);
        
        // Все транзитивные альтернативы должны быть в системе
        for (Knife knife : transitive) {
            assertThat(knifeRepository.findById(knife.getId())).isPresent();
        }
    }
    
    /**
     * Unit тест: Поиск транзитивных для ножа без альтернатив
     * 
     * Для ножа без альтернатив findTransitive должен вернуть пустое множество.
     */
    @Test
    public void testTransitiveAlternativesForKnifeWithoutAlternatives() {
        Set<Long> newAlternatives = new HashSet<>();
        
        Set<Knife> transitive = transitiveAlternativesService.findTransitive(knife1.getId(), newAlternatives);
        
        // Должно быть пусто
        assertThat(transitive).isEmpty();
    }
}
