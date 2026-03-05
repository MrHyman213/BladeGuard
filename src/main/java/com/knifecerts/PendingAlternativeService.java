package com.knifecerts;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.PendingAlternative;
import com.knifecerts.model.Submission;
import com.knifecerts.repository.PendingAlternativeRepository;

@Service
@Transactional
public class PendingAlternativeService {
    
    private static final Logger logger = Logger.getLogger(PendingAlternativeService.class.getName());
    
    private final PendingAlternativeRepository pendingAlternativeRepository;
    
    public PendingAlternativeService(PendingAlternativeRepository pendingAlternativeRepository) {
        this.pendingAlternativeRepository = pendingAlternativeRepository;
    }
    
    public PendingAlternative savePendingAlternative(Submission submission, String brandName, String knifeName) {
        logger.info("Saving pending alternative for submission #" + submission.getId());
        
        PendingAlternative pendingAlt = new PendingAlternative();
        pendingAlt.setSubmission(submission);
        pendingAlt.setBrandName(brandName);
        pendingAlt.setKnifeName(knifeName);
        
        return pendingAlternativeRepository.save(pendingAlt);
    }
    
    public List<PendingAlternative> getPendingAlternatives(Submission submission) {
        logger.info("Getting pending alternatives for submission #" + submission.getId());
        return pendingAlternativeRepository.findBySubmission(submission);
    }
    
    public void deletePendingAlternatives(Submission submission) {
        logger.info("Deleting pending alternatives for submission #" + submission.getId());
        pendingAlternativeRepository.deleteBySubmission(submission);
    }
    
    public void deletePendingAlternative(Long id) {
        logger.info("Deleting pending alternative #" + id);
        pendingAlternativeRepository.deleteById(id);
    }
}
