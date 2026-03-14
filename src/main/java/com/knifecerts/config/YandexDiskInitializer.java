package com.knifecerts.config;

import java.io.IOException;

import com.knifecerts.service.YandexDiskService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Order(2)
public class YandexDiskInitializer implements CommandLineRunner {

    private final com.knifecerts.service.YandexDiskService yandexDiskService;
    private final RestTemplate restTemplate = new RestTemplate();

    public YandexDiskInitializer(YandexDiskService yandexDiskService) {
        this.yandexDiskService = yandexDiskService;
    }

    @Override
    public void run(String... args) {
        System.out.println("Initializing Yandex.Disk folders...");
        
        try {
            ensureFolderExists("app:/offers");
            ensureFolderExists("app:/certificates");
            System.out.println("Yandex.Disk folders initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize Yandex.Disk folders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureFolderExists(String folderPath) throws IOException {
        try {
            String checkUrl = UriComponentsBuilder
                    .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                    .queryParam("path", folderPath)
                    .build()
                    .toUriString();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "OAuth " + getToken());
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(checkUrl, HttpMethod.GET, entity, String.class);
            System.out.println("Folder exists: " + folderPath);
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                System.out.println("Folder not found, creating: " + folderPath);
                createFolder(folderPath);
            } else {
                throw new IOException("Error checking folder: " + e.getMessage(), e);
            }
        }
    }

    private void createFolder(String folderPath) throws IOException {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                .queryParam("path", folderPath)
                .build()
                .toUriString();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + getToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            System.out.println("Folder created successfully: " + folderPath);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("409")) {
                System.out.println("Folder already exists: " + folderPath);
            } else {
                throw new IOException("Failed to create folder: " + e.getMessage(), e);
            }
        }
    }

    private String getToken() {
        try {
            java.lang.reflect.Field tokenField = YandexDiskService.class.getDeclaredField("token");
            tokenField.setAccessible(true);
            String token = (String) tokenField.get(yandexDiskService);
            return token != null ? token.trim() : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get token", e);
        }
    }
}
