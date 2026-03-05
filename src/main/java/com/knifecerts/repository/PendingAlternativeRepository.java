package com.knifecerts.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.PendingAlternative;
import com.knifecerts.model.Submission;

@Repository
public interface PendingAlternativeRepository extends JpaRepository<PendingAlternative, Long> {
    
    List<PendingAlternative> findBySubmissionId(Long submissionId);
    
    List<PendingAlternative> findBySubmission(Submission submission);
    
    void deleteBySubmissionId(Long submissionId);
    
    void deleteBySubmission(Submission submission);
}
