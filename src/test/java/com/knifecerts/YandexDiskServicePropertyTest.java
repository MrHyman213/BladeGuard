package com.knifecerts;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based тесты для YandexDiskService
 * Feature: certificate-submission-system
 */
class YandexDiskServicePropertyTest {

    /**
     * Feature: certificate-submission-system, Property 7: Уникальность Имен Файлов
     * **Validates: Requirements 4.2**
     * 
     * Для любых двух заявок, сгенерированные имена файлов должны быть уникальными,
     * даже если заявки создаются одновременно.
     */
    @Property(tries = 100)
    void generatedFileNamesShouldBeUnique(@ForAll("fileNames") String originalName) throws Exception {
        YandexDiskService service = new YandexDiskService();
        
        // Используем рефлексию для доступа к приватному методу
        Method generateMethod = YandexDiskService.class.getDeclaredMethod("generateUniqueFileName", String.class);
        generateMethod.setAccessible(true);
        
        Set<String> generatedNames = new HashSet<>();
        int iterations = 10;
        
        for (int i = 0; i < iterations; i++) {
            String fileName = (String) generateMethod.invoke(service, originalName);
            
            // Проверяем формат имени файла
            assertThat(fileName).matches("photo_\\d{8}_\\d{6}_[a-z0-9]{6}\\.jpg");
            
            // Проверяем уникальность
            assertThat(generatedNames).doesNotContain(fileName);
            generatedNames.add(fileName);
            
            // Небольшая задержка для обеспечения разных временных меток
            Thread.sleep(1);
        }
        
        // Все имена должны быть уникальными
        assertThat(generatedNames).hasSize(iterations);
    }
    
    /**
     * Тест на уникальность имен файлов при параллельной генерации
     * Проверяет, что даже при одновременном создании заявок имена файлов уникальны
     */
    @Test
    void generatedFileNamesShouldBeUniqueInConcurrentEnvironment() throws Exception {
        YandexDiskService service = new YandexDiskService();
        
        Method generateMethod = YandexDiskService.class.getDeclaredMethod("generateUniqueFileName", String.class);
        generateMethod.setAccessible(true);
        
        int threadCount = 10;
        int iterationsPerThread = 10;
        Set<String> allGeneratedNames = ConcurrentHashMap.newKeySet();
        AtomicInteger collisionCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        String fileName = (String) generateMethod.invoke(service, "test.jpg");
                        
                        // Проверяем формат
                        assertThat(fileName).matches("photo_\\d{8}_\\d{6}_[a-z0-9]{6}\\.jpg");
                        
                        // Пытаемся добавить в множество
                        boolean added = allGeneratedNames.add(fileName);
                        if (!added) {
                            collisionCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // Проверяем, что не было коллизий
        assertThat(collisionCount.get()).isEqualTo(0);
        assertThat(allGeneratedNames).hasSize(threadCount * iterationsPerThread);
    }
    
    @Provide
    Arbitrary<String> fileNames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('.', '_', '-')
                .ofMinLength(1)
                .ofMaxLength(50);
    }
}
