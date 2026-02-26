package com.knifecerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Logger;

@Service
public class YandexDiskService {

    private static final Logger logger = Logger.getLogger(YandexDiskService.class.getName());

    @Value("${yandex.disk.token}")
    private String token;

    @Value("${yandex.disk.folder}")
    private String folder;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String uploadPhoto(InputStream photoStream, String fileName) throws IOException {
        ensureFolderExists();
        
        String path = folder + "/" + fileName;
        
        File tempFile = File.createTempFile("telegram_photo_", ".jpg");
        try {
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = photoStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            String uploadUrl = getUploadUrl(path);
            uploadFile(uploadUrl, tempFile);
            
            return path;
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    private void ensureFolderExists() {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                    .queryParam("path", folder)
                    .build()
                    .toUriString();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "OAuth " + token);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            logger.info("Folder created: " + folder);
        } catch (Exception e) {
            logger.info("Folder already exists or error: " + e.getMessage());
        }
    }

    private String getUploadUrl(String path) throws IOException {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources/upload")
                .queryParam("path", path)
                .queryParam("overwrite", "true")
                .build()
                .toUriString();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + token);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("href").asText();
        } catch (Exception e) {
            logger.severe("Failed to get upload URL. Check if OAuth token has 'cloud_api:disk.write' permission");
            throw new IOException("Ошибка доступа к Яндекс.Диску. Проверьте права токена OAuth.", e);
        }
    }

    private void uploadFile(String uploadUrl, File file) throws IOException {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        
        HttpEntity<byte[]> entity = new HttpEntity<>(fileContent, headers);
        
        restTemplate.exchange(uploadUrl, HttpMethod.PUT, entity, String.class);
    }


    public JsonNode listPhotos() throws IOException {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                .queryParam("path", folder)
                .queryParam("limit", "100")
                .queryParam("fields", "items.name,items.path,items.created,items.file")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            logger.severe("Failed to list photos: " + e.getMessage());
            throw new IOException("Ошибка получения списка файлов", e);
        }
    }

    public String getDownloadUrl(String filePath) throws IOException {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources/download")
                .queryParam("path", filePath)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("href").asText();
        } catch (Exception e) {
            logger.severe("Failed to get download URL: " + e.getMessage());
            throw new IOException("Ошибка получения ссылки на скачивание", e);
        }
    }

    public InputStream downloadPhoto(String filePath) throws IOException {
        String downloadUrl = getDownloadUrl(filePath);
        return new java.net.URL(downloadUrl).openStream();
    }

    public String getFolder() {
        return folder;
    }

}
