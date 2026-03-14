package com.knifecerts.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.SubmissionBuffer;
import com.knifecerts.model.SubmissionStatus;

@Repository
public interface SubmissionBufferRepository extends JpaRepository<SubmissionBuffer, Long> {
    
    List<SubmissionBuffer> findByUserId(Long userId);
    
    @Query("SELECT s FROM SubmissionBuffer s ORDER BY s.createdAt ASC")
    List<SubmissionBuffer> findAllOrderByCreatedAt();
    
    @Query("SELECT s FROM SubmissionBuffer s WHERE s.brandName = :brandName AND s.modelName = :modelName")
    List<SubmissionBuffer> findByBrandNameAndModelName(@Param("brandName") String brandName, @Param("modelName") String modelName);
    
    @Query("SELECT sb FROM SubmissionBuffer sb " +
           "LEFT JOIN FETCH sb.alternatives " +
           "WHERE sb.id = :id")
    Optional<SubmissionBuffer> findByIdWithAlternatives(@Param("id") Long id);
    
    @Query("SELECT sb FROM SubmissionBuffer sb " +
           "LEFT JOIN FETCH sb.alternatives " +
           "WHERE sb.status = :status")
    List<SubmissionBuffer> findByStatusWithAlternatives(@Param("status") SubmissionStatus status);
}