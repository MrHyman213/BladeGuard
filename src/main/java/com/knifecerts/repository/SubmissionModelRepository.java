package com.knifecerts.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.KnifeModel;
import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionModel;

/**
 * Repository для работы со связями между заявками и моделями ножей.
 */
@Repository
public interface SubmissionModelRepository extends JpaRepository<SubmissionModel, Long> {
    
    /**
     * Находит все связи для конкретной заявки.
     * 
     * @param submission заявка
     * @return список связей
     */
    List<SubmissionModel> findBySubmission(Submission submission);
    
    /**
     * Находит конкретную связь между заявкой и моделью ножа.
     * 
     * @param submission заявка
     * @param knifeModel модель ножа
     * @return Optional со связью, если найдена
     */
    Optional<SubmissionModel> findBySubmissionAndKnifeModel(Submission submission, KnifeModel knifeModel);
    
    /**
     * Удаляет все связи для конкретной заявки.
     * 
     * @param submission заявка
     */
    void deleteBySubmission(Submission submission);
    
    /**
     * Находит все заявки, связанные с моделью ножа по нормализованному названию.
     * 
     * @param normalizedName нормализованное название модели
     * @return список заявок
     */
    @Query("SELECT DISTINCT sm.submission FROM SubmissionModel sm " +
           "WHERE sm.knifeModel.normalizedName = :normalizedName " +
           "AND sm.submission.status = com.knifecerts.model.SubmissionStatus.APPROVED")
    List<Submission> findSubmissionsByKnifeModelNormalizedName(@Param("normalizedName") String normalizedName);
    
    /**
     * Находит основную (primary) заявку для модели ножа.
     * 
     * @param normalizedName нормализованное название модели
     * @return Optional с заявкой, если найдена
     */
    @Query("SELECT sm.submission FROM SubmissionModel sm " +
           "WHERE sm.knifeModel.normalizedName = :normalizedName " +
           "AND sm.isPrimary = true " +
           "AND sm.submission.status = com.knifecerts.model.SubmissionStatus.APPROVED")
    List<Submission> findPrimarySubmissionByKnifeModelNormalizedName(@Param("normalizedName") String normalizedName);
}
