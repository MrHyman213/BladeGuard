package com.knifecerts.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionAlternative;

@Repository
public interface SubmissionAlternativeRepository extends JpaRepository<SubmissionAlternative, Long> {
    
    @Query("SELECT sa.alternative FROM SubmissionAlternative sa WHERE sa.submission.id = :submissionId")
    List<Submission> findAlternativesBySubmissionId(@Param("submissionId") Long submissionId);
    
    @Query("SELECT sa FROM SubmissionAlternative sa WHERE sa.submission.id = :submissionId AND sa.alternative.id = :alternativeId")
    SubmissionAlternative findBySubmissionIdAndAlternativeId(@Param("submissionId") Long submissionId, @Param("alternativeId") Long alternativeId);
    
    void deleteBySubmissionIdAndAlternativeId(Long submissionId, Long alternativeId);
}
