package com.knifecerts;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.PhotoHistory;
import com.knifecerts.model.Submission;
import com.knifecerts.repository.PhotoHistoryRepository;
import com.knifecerts.repository.SubmissionRepository;

@Service
@Transactional
public class PhotoHistoryService {
    
    private static final Logger logger = Logger.getLogger(PhotoHistoryService.class.getName());
    
    private final PhotoHistoryRepository photoHistoryRepository;
    private final SubmissionRepository submissionRepository;
    
    public PhotoHistoryService(PhotoHistoryRepository photoHistoryRepository, 
                              SubmissionRepository submissionRepository) {
        this.photoHistoryRepository = photoHistoryRepository;
        this.submissionRepository = submissionRepository;
    }
    
    public PhotoHistory replacePhoto(Submission submission, String newPath, Long moderatorId, String reason) {
        logger.info("Replacing photo for submission #" + submission.getId());
        
        String oldPath = submission.getPhotoPath();
        
        PhotoHistory history = new PhotoHistory();
        history.setSubmission(submission);
        history.setOldPath(oldPath);
        history.setNewPath(newPath);
        history.setReplacedBy(moderatorId);
        history.setReason(reason);
        
        PhotoHistory saved = photoHistoryRepository.save(history);
        
        submission.setPhotoPath(newPath);
        submissionRepository.save(submission);
        
        logger.info("Photo replaced for submission #" + submission.getId());
        return saved;
    }
    
    public List<PhotoHistory> getPhotoHistory(Submission submission) {
        logger.info("Getting photo history for submission #" + submission.getId());
        return photoHistoryRepository.findBySubmissionOrderByReplacedAtDesc(submission);
    }
    
    public void rollbackPhoto(Long historyId) {
        logger.info("Rolling back photo history #" + historyId);
        
        Optional<PhotoHistory> historyOpt = photoHistoryRepository.findById(historyId);
        if (historyOpt.isEmpty()) {
            logger.warning("Photo history #" + historyId + " not found");
            return;
        }
        
        PhotoHistory history = historyOpt.get();
        Submission submission = history.getSubmission();
        
        submission.setPhotoPath(history.getOldPath());
        submissionRepository.save(submission);
        
        photoHistoryRepository.delete(history);
        
        logger.info("Photo rolled back for submission #" + submission.getId());
    }
}
