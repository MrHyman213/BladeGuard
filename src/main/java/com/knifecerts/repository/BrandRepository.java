package com.knifecerts.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.Brand;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {
    
    Optional<Brand> findByName(String name);
    
    @Query("SELECT b FROM Brand b ORDER BY b.name ASC")
    List<Brand> findAllOrderByName();
    
    @Query("SELECT b FROM Brand b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY b.name ASC")
    List<Brand> searchByName(@Param("query") String query);
}
