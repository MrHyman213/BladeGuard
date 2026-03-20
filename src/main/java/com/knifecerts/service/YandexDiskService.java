package com.knifecerts.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class YandexDiskService {

    private static final Logger logger = Logger.getLogger(YandexDiskService.class.getName());

    @Value("${yandex.disk.token}")
    private String token;

    @Value("${yandex.disk.folder}")
    private String folder;
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private String getCleanToken() {
        return token != null ? token.trim() : null;
    }

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Генерирует уникальное имя файла в формате: photo_<timestamp>_<random>.jpg
     * 
     * @param originalName оригинальное имя файла (не используется, но может быть полезно для расширения)
     * @return уникальное имя файла
     */
    private String generateUniqueFileName(String originalName) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String randomPart = generateRandomString(6);
        return "photo_" + timestamp + "_" + randomPart + ".jpg";
    }
    
    /**
     * Генерирует случайную строку заданной длины из символов a-z и 0-9
     * 
     * @param length длина строки
     * @return случайная строка
     */
    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(RANDOM_CHARS.length());
            sb.append(RANDOM_CHARS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Загружает фото в папку certificates с указанным именем файла.
     *
     * @param photoStream поток с данными фото
     * @param fileName имя файла
     * @return путь к загруженному файлу на Yandex.Disk (app:/certificates/fileName)
     * @throws IOException если загрузка не удалась
     */
    public String uploadToCertificates(InputStream photoStream, String fileName) throws IOException {
        String path = "app:/certificates/" + fileName;
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
            logger.info("Файл загружен в certificates: " + path);
            return path;
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    public String uploadPhoto(InputStream photoStream, String fileName) throws IOException {
        // Проверяем и создаем папку если её нет
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

    /**
     * Загружает фото в папку offers с автоматической генерацией уникального имени файла
     * и логикой повторных попыток при ошибках.
     * 
     * @param photoStream поток с данными фото
     * @param originalFileName оригинальное имя файла (используется для генерации уникального имени)
     * @return путь к загруженному файлу на Yandex.Disk
     * @throws IOException если загрузка не удалась после всех попыток
     */
    public String uploadToOffers(InputStream photoStream, String originalFileName) throws IOException {
        String uniqueFileName = generateUniqueFileName(originalFileName);
        String offersPath = "app:/offers/" + uniqueFileName;
        
        int maxRetries = 3;
        IOException lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("Попытка загрузки файла " + uniqueFileName + " (попытка " + attempt + " из " + maxRetries + ")");
                
                File tempFile = File.createTempFile("telegram_photo_", ".jpg");
                try {
                    // Сохраняем поток во временный файл
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = photoStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    // Получаем URL для загрузки
                    String uploadUrl = getUploadUrl(offersPath);
                    
                    // Загружаем файл
                    uploadFile(uploadUrl, tempFile);
                    
                    logger.info("Файл успешно загружен: " + offersPath);
                    return offersPath;
                    
                } finally {
                    Files.deleteIfExists(tempFile.toPath());
                }
                
            } catch (IOException e) {
                lastException = e;
                logger.warning("Ошибка при загрузке файла (попытка " + attempt + "): " + e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        // Экспоненциальная задержка: 1s, 2s, 4s
                        long delayMs = (long) Math.pow(2, attempt - 1) * 1000;
                        logger.info("Ожидание " + delayMs + "ms перед следующей попыткой");
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Загрузка прервана", ie);
                    }
                }
            }
        }
        
        // Если все попытки не удались
        logger.severe("Не удалось загрузить файл после " + maxRetries + " попыток");
        throw new IOException("Не удалось загрузить файл на Yandex.Disk после " + maxRetries + " попыток", lastException);
    }

    private void ensureFolderExists() throws IOException {
        // Логируем токен для отладки (первые и последние 5 символов)
        String cleanToken = getCleanToken();
        if (cleanToken != null && cleanToken.length() > 10) {
            logger.info("Token loaded: " + cleanToken.substring(0, 5) + "..." + cleanToken.substring(cleanToken.length() - 5) + " (length: " + cleanToken.length() + ")");
        } else {
            logger.severe("Token is null or too short: " + cleanToken);
        }
        logger.info("Folder path: " + folder);
        
        // Логируем полный заголовок Authorization
        String authHeader = "OAuth " + cleanToken;
        logger.info("Authorization header: " + authHeader.substring(0, Math.min(15, authHeader.length())) + "... (total length: " + authHeader.length() + ")");
        
        try {
            // Проверяем существование папки
            String checkUrl = UriComponentsBuilder
                    .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                    .queryParam("path", folder)
                    .build()
                    .toUriString();
            
            logger.info("Request URL: " + checkUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(checkUrl, HttpMethod.GET, entity, String.class);
            logger.info("Folder exists: " + folder);
            
        } catch (Exception e) {
            // Папка не существует, создаем её
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                logger.info("Folder not found, creating: " + folder);
                createFolder();
            } else {
                logger.warning("Error checking folder: " + e.getMessage());
            }
        }
    }

    private void createFolder() throws IOException {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                .queryParam("path", folder)
                .build()
                .toUriString();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + getCleanToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            logger.info("Folder created successfully: " + folder);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("409")) {
                logger.info("Folder already exists: " + folder);
            } else {
                logger.severe("Failed to create folder: " + e.getMessage());
                throw new IOException("Не удалось создать папку на Яндекс.Диске", e);
            }
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
        headers.set("Authorization", "OAuth " + getCleanToken());
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("href").asText();
        } catch (Exception e) {
            logger.severe("Failed to get upload URL: " + e.getMessage());
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
        headers.set("Authorization", "OAuth " + getCleanToken());

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
        headers.set("Authorization", "OAuth " + getCleanToken());

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

    /**
     * Перемещает файл из одной папки в другую на Yandex.Disk
     * 
     * @param sourcePath исходный путь к файлу (например, "app:/offers/photo.jpg")
     * @param destinationPath целевой путь к файлу (например, "app:/certificates/photo.jpg")
     * @throws IOException если операция перемещения не удалась
     */
    public void moveFile(String sourcePath, String destinationPath) throws IOException {
        logger.info("DEBUG: Attempting to move file from: " + sourcePath + " to: " + destinationPath);
        
        String url = UriComponentsBuilder
                .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources/move")
                .queryParam("from", sourcePath)
                .queryParam("path", destinationPath)
                .queryParam("overwrite", "false")
                .build()
                .toUriString();
        
        logger.info("DEBUG: Move URL: " + url);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + getCleanToken());
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            logger.info("Файл успешно перемещен: " + sourcePath + " -> " + destinationPath);
        } catch (Exception e) {
            logger.severe("Не удалось переместить файл: " + e.getMessage());
            logger.severe("DEBUG: Full exception: " + e.getClass().getName());
            if (e.getCause() != null) {
                logger.severe("DEBUG: Cause: " + e.getCause().getMessage());
            }
            throw new IOException("Ошибка перемещения файла на Yandex.Disk", e);
        }
    }

    /**
     * Удаляет файл с Yandex.Disk
     * 
     * @param filePath путь к файлу для удаления (например, "app:/offers/photo.jpg")
     * @throws IOException если операция удаления не удалась
     */
    public void deleteFile(String filePath) throws IOException {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                .queryParam("path", filePath)
                .queryParam("permanently", "true")
                .build()
                .toUriString();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + getCleanToken());
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            logger.info("Файл успешно удален: " + filePath);
        } catch (Exception e) {
            logger.severe("Не удалось удалить файл: " + e.getMessage());
            throw new IOException("Ошибка удаления файла с Yandex.Disk", e);
        }
    }

    public String getFolder() {
        return folder;
    }
    
    private boolean folderExists(String folderPath) {
        try {
            String checkUrl = UriComponentsBuilder
                    .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                    .queryParam("path", folderPath)
                    .build()
                    .toUriString();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "OAuth " + getCleanToken());
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(checkUrl, HttpMethod.GET, entity, String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public int clearFolder(String folderPath) throws IOException {
        // Проверяем существование папки
        if (!folderExists(folderPath)) {
            logger.info("Папка " + folderPath + " не существует, пропускаем очистку");
            return 0;
        }
        
        String url = UriComponentsBuilder
                .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                .queryParam("path", folderPath)
                .queryParam("limit", "1000")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + getCleanToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            
            // Проверяем наличие _embedded и items
            JsonNode embedded = jsonNode.get("_embedded");
            if (embedded == null) {
                logger.info("Папка " + folderPath + " пуста");
                return 0;
            }
            
            JsonNode items = embedded.get("items");
            if (items == null || !items.isArray() || items.size() == 0) {
                logger.info("Папка " + folderPath + " пуста");
                return 0;
            }
            
            // Удаляем все файлы
            int deletedCount = 0;
            for (JsonNode item : items) {
                String path = item.get("path").asText();
                deleteFile(path);
                deletedCount++;
                logger.info("Удален файл: " + path);
            }
            
            logger.info("Папка " + folderPath + " очищена. Удалено файлов: " + deletedCount);
            return deletedCount;
        } catch (Exception e) {
            logger.severe("Ошибка при очистке папки: " + e.getMessage());
            throw new IOException("Ошибка очистки папки", e);
        }
    }
    
    /**
     * Получает список файлов в указанной папке на Yandex.Disk
     * 
     * @param folderPath путь к папке (например, "app:/certificates")
     * @return список полных путей к файлам
     * @throws IOException если операция не удалась
     */
    public List<String> listFiles(String folderPath) throws IOException {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://cloud-api.yandex.net/v1/disk/resources")
                .queryParam("path", folderPath)
                .queryParam("limit", "1000")
                .build()
                .toUriString();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + getCleanToken());
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            List<String> filePaths = new ArrayList<>();
            
            // Парсим JSON ответ для получения списка файлов
            String responseBody = response.getBody();
            if (responseBody != null && responseBody.contains("\"items\"")) {
                // Простой парсинг JSON для получения путей файлов
                String[] items = responseBody.split("\"path\":");
                for (int i = 1; i < items.length; i++) {
                    String pathPart = items[i].split(",")[0].trim();
                    pathPart = pathPart.replace("\"", "");
                    if (pathPart.startsWith("disk:") || pathPart.startsWith("app:")) {
                        filePaths.add(pathPart);
                    }
                }
            }
            
            logger.info("Найдено файлов в " + folderPath + ": " + filePaths.size());
            return filePaths;
        } catch (Exception e) {
            logger.warning("Не удалось получить список файлов: " + e.getMessage());
            throw new IOException("Ошибка получения списка файлов с Yandex.Disk", e);
        }
    }
}
