package com.knifecerts.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.KnifeModel;

/**
 * Repository для работы с моделями ножей.
 */
@Repository
public interface KnifeModelRepository extends JpaRepository<KnifeModel, Long> {
    
    /**
     * Находит модель ножа по нормализованному названию.
     * 
     * @param normalizedName нормализованное название
     * @return Optional с моделью, если найдена
     */
    Optional<KnifeModel> findByNormalizedName(String normalizedName);
    
    /**
     * Находит модель ножа по точному названию.
     * 
     * @param name название модели
     * @return Optional с моделью, если найдена
     */
    Optional<KnifeModel> findByName(String name);
}
