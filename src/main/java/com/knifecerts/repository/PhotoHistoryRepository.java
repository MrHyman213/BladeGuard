package com.knifecerts.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.PhotoHistory;
import com.knifecerts.model.Submission;

@Repository
public interface PhotoHistoryRepository extends JpaRepository<PhotoHistory, Long> {
    
    List<PhotoHistory> findBySubmissionIdOrderByReplacedAtDesc(Long submissionId);
    
    List<PhotoHistory> findBySubmissionOrderByReplacedAtDesc(Submission submission);
}
