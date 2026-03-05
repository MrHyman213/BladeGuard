package com.knifecerts;

import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knifecerts.dto.RejectSubmissionRequest;
import com.knifecerts.dto.SubmissionDto;
import com.knifecerts.dto.UpdateSubmissionRequest;
import com.knifecerts.model.ModerationSession;
import com.knifecerts.model.Submission;

/**
 * REST API контроллер для модерации через Web App.
 * 
 * Предоставляет endpoints для получения, обновления, одобрения и отклонения заявок.
 * Все операции требуют валидный токен сессии.
 */
@RestController
@RequestMapping("/api/moderation")
public class ModerationApiController {
    
    private static final Logger logger = Logger.getLogger(ModerationApiController.class.getName());
    
    private final ModerationSessionManager sessionManager;
    private final SubmissionService submissionService;
    private final YandexDiskService yandexDiskService;
    
    public ModerationApiController(ModerationSessionManager sessionManager,
                                  SubmissionService submissionService,
                                  YandexDiskService yandexDiskService) {
        this.sessionManager = sessionManager;
        this.submissionService = submissionService;
        this.yandexDiskService = yandexDiskService;
    }
    
    /**
     * Получить данные заявки по токену сессии.
     * 
     * GET /api/moderation/submission/{token}
     */
    @GetMapping("/submission/{token}")
    public ResponseEntity<SubmissionDto> getSubmission(@PathVariable String token) {
        logger.info("Запрос заявки по токену: " + token);
        
        Optional<ModerationSession> sessionOpt = sessionManager.validateToken(token);
        
        if (sessionOpt.isEmpty()) {
            logger.warning("Невалидный токен: " + token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        ModerationSession session = sessionOpt.get();
        Optional<Submission> submissionOpt = submissionService.getSubmissionById(session.getSubmissionId());
        
        if (submissionOpt.isEmpty()) {
            logger.warning("Заявка не найдена: " + session.getSubmissionId());
            return ResponseEntity.notFound().build();
        }
        
        Submission submission = submissionOpt.get();
        
        try {
            // Получить URL фото
            String photoUrl = yandexDiskService.getDownloadUrl(submission.getPhotoPath());
            
            SubmissionDto dto = new SubmissionDto();
            dto.setId(submission.getId());
            dto.setName(submission.getName());
            dto.setBrand(submission.getBrand() != null ? submission.getBrand().getName() : null);
            dto.setIndexCode(submission.getIndexCode());
            dto.setAlternativeModels(submission.getAlternativeModelsList());
            dto.setPhotoUrl(photoUrl);
            dto.setUsername(submission.getUsername());
            dto.setCreatedAt(submission.getCreatedAt());
            
            logger.info("Заявка отправлена: " + submission.getId());
            return ResponseEntity.ok(dto);
            
        } catch (Exception e) {
            logger.severe("Ошибка получения URL фото: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Обновить данные заявки.
     * 
     * PUT /api/moderation/submission/{token}
     */
    @PutMapping("/submission/{token}")
    public ResponseEntity<Void> updateSubmission(
            @PathVariable String token,
            @RequestBody UpdateSubmissionRequest request) {
        
        logger.info("Запрос обновления заявки по токену: " + token);
        
        Optional<ModerationSession> sessionOpt = sessionManager.validateToken(token);
        
        if (sessionOpt.isEmpty()) {
            logger.warning("Невалидный токен: " + token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        ModerationSession session = sessionOpt.get();
        
        try {
            submissionService.updateSubmission(
                session.getSubmissionId(),
                request.getName(),
                request.getBrand(),
                request.getIndexCode(),
                request.getAlternativeModels()
            );
            
            logger.info("Заявка обновлена: " + session.getSubmissionId());
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            logger.severe("Ошибка обновления заявки: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Одобрить заявку.
     * 
     * POST /api/moderation/submission/{token}/approve
     */
    @PostMapping("/submission/{token}/approve")
    public ResponseEntity<Void> approveSubmission(@PathVariable String token) {
        logger.info("Запрос одобрения заявки по токену: " + token);
        
        Optional<ModerationSession> sessionOpt = sessionManager.validateToken(token);
        
        if (sessionOpt.isEmpty()) {
            logger.warning("Невалидный токен: " + token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        ModerationSession session = sessionOpt.get();
        
        try {
            submissionService.approveSubmission(session.getSubmissionId(), session.getModeratorId());
            sessionManager.markAsUsed(token);
            
            logger.info("Заявка одобрена: " + session.getSubmissionId());
            return ResponseEntity.ok().build();
            
        } catch (SubmissionException e) {
            logger.warning("Ошибка одобрения: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    /**
     * Отклонить заявку с причиной.
     * 
     * POST /api/moderation/submission/{token}/reject
     */
    @PostMapping("/submission/{token}/reject")
    public ResponseEntity<Void> rejectSubmission(
            @PathVariable String token,
            @RequestBody RejectSubmissionRequest request) {
        
        logger.info("Запрос отклонения заявки по токену: " + token);
        
        Optional<ModerationSession> sessionOpt = sessionManager.validateToken(token);
        
        if (sessionOpt.isEmpty()) {
            logger.warning("Невалидный токен: " + token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        ModerationSession session = sessionOpt.get();
        
        try {
            submissionService.rejectSubmissionWithReason(
                session.getSubmissionId(),
                session.getModeratorId(),
                request.getReason()
            );
            sessionManager.markAsUsed(token);
            
            logger.info("Заявка отклонена: " + session.getSubmissionId());
            return ResponseEntity.ok().build();
            
        } catch (SubmissionException e) {
            logger.warning("Ошибка отклонения: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
