package com.knifecerts.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.KnifeModel;

@Repository
public interface KnifeModelRepository extends JpaRepository<KnifeModel, Long> {

    Optional<KnifeModel> findByName(String name);

    boolean existsByName(String name);
    
    @Query("SELECT m FROM KnifeModel m ORDER BY m.name ASC")
    List<KnifeModel> findAllOrderByName();
    
    @Query("SELECT m FROM KnifeModel m WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY m.name ASC")
    List<KnifeModel> searchByName(@Param("query") String query);
}
