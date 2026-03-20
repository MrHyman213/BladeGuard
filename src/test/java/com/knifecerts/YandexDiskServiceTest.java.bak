package com.knifecerts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Юнит-тесты для YandexDiskService
 */
@ExtendWith(MockitoExtension.class)
class YandexDiskServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private YandexDiskService yandexDiskService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(yandexDiskService, "token", "test-token");
        ReflectionTestUtils.setField(yandexDiskService, "folder", "app:/test");
        ReflectionTestUtils.setField(yandexDiskService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(yandexDiskService, "objectMapper", objectMapper);
    }

    @Test
    void uploadToOffers_shouldSucceedOnFirstAttempt() throws IOException {
        // Arrange
        byte[] photoData = "test photo data".getBytes();
        InputStream photoStream = new ByteArrayInputStream(photoData);
        String originalFileName = "test.jpg";

        // Mock getUploadUrl response
        String uploadUrlResponse = "{\"href\":\"https://upload.url\"}";
        when(restTemplate.exchange(
                contains("resources/upload"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(uploadUrlResponse, HttpStatus.OK));

        // Mock uploadFile response
        when(restTemplate.exchange(
                eq("https://upload.url"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act
        String result = yandexDiskService.uploadToOffers(photoStream, originalFileName);

        // Assert
        assertThat(result).startsWith("app:/offers/photo_");
        assertThat(result).endsWith(".jpg");
        verify(restTemplate, times(2)).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void uploadToOffers_shouldRetryOnFailureAndSucceed() throws IOException {
        // Arrange
        byte[] photoData = "test photo data".getBytes();
        InputStream photoStream = new ByteArrayInputStream(photoData);
        String originalFileName = "test.jpg";

        String uploadUrlResponse = "{\"href\":\"https://upload.url\"}";

        // First attempt fails, second succeeds
        when(restTemplate.exchange(
                contains("resources/upload"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        ))
                .thenThrow(new RestClientException("Network error"))
                .thenReturn(new ResponseEntity<>(uploadUrlResponse, HttpStatus.OK));

        when(restTemplate.exchange(
                eq("https://upload.url"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act
        String result = yandexDiskService.uploadToOffers(photoStream, originalFileName);

        // Assert
        assertThat(result).startsWith("app:/offers/photo_");
        verify(restTemplate, times(3)).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void uploadToOffers_shouldFailAfterMaxRetries() {
        // Arrange
        byte[] photoData = "test photo data".getBytes();
        InputStream photoStream = new ByteArrayInputStream(photoData);
        String originalFileName = "test.jpg";

        // All attempts fail
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RestClientException("Network error"));

        // Act & Assert
        assertThatThrownBy(() -> yandexDiskService.uploadToOffers(photoStream, originalFileName))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("после 3 попыток");

        // Should have tried 3 times
        verify(restTemplate, times(3)).exchange(
                contains("resources/upload"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void moveFile_shouldSuccessfullyMoveFile() throws IOException {
        // Arrange
        String sourcePath = "app:/offers/photo.jpg";
        String destinationPath = "app:/certificates/photo.jpg";

        when(restTemplate.exchange(
                contains("resources/move"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act
        yandexDiskService.moveFile(sourcePath, destinationPath);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );

        String capturedUrl = urlCaptor.getValue();
        assertThat(capturedUrl).contains("resources/move");
        assertThat(capturedUrl).contains("from=" + sourcePath);
        assertThat(capturedUrl).contains("path=" + destinationPath);
    }

    @Test
    void moveFile_shouldThrowExceptionOnFailure() {
        // Arrange
        String sourcePath = "app:/offers/photo.jpg";
        String destinationPath = "app:/certificates/photo.jpg";

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RestClientException("Move failed"));

        // Act & Assert
        assertThatThrownBy(() -> yandexDiskService.moveFile(sourcePath, destinationPath))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Ошибка перемещения файла");
    }

    @Test
    void deleteFile_shouldSuccessfullyDeleteFile() throws IOException {
        // Arrange
        String filePath = "app:/offers/photo.jpg";

        when(restTemplate.exchange(
                contains("resources"),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act
        yandexDiskService.deleteFile(filePath);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(String.class)
        );

        String capturedUrl = urlCaptor.getValue();
        assertThat(capturedUrl).contains("path=" + filePath);
        assertThat(capturedUrl).contains("permanently=true");
    }

    @Test
    void deleteFile_shouldThrowExceptionOnFailure() {
        // Arrange
        String filePath = "app:/offers/photo.jpg";

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RestClientException("Delete failed"));

        // Act & Assert
        assertThatThrownBy(() -> yandexDiskService.deleteFile(filePath))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Ошибка удаления файла");
    }

    @Test
    void uploadToOffers_shouldGenerateUniqueFileNames() throws Exception {
        // Arrange
        byte[] photoData = "test photo data".getBytes();
        String originalFileName = "test.jpg";

        String uploadUrlResponse = "{\"href\":\"https://upload.url\"}";
        when(restTemplate.exchange(
                contains("resources/upload"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(uploadUrlResponse, HttpStatus.OK));

        when(restTemplate.exchange(
                eq("https://upload.url"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act - upload multiple times
        String result1 = yandexDiskService.uploadToOffers(new ByteArrayInputStream(photoData), originalFileName);
        Thread.sleep(10); // Small delay to ensure different timestamps
        String result2 = yandexDiskService.uploadToOffers(new ByteArrayInputStream(photoData), originalFileName);

        // Assert - file names should be different
        assertThat(result1).isNotEqualTo(result2);
        assertThat(result1).matches("app:/offers/photo_\\d{8}_\\d{6}_[a-z0-9]{6}\\.jpg");
        assertThat(result2).matches("app:/offers/photo_\\d{8}_\\d{6}_[a-z0-9]{6}\\.jpg");
    }
}
