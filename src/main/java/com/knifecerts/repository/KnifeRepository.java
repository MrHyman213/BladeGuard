package com.knifecerts.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.Brand;
import com.knifecerts.model.Knife;
import com.knifecerts.model.KnifeModel;

@Repository
public interface KnifeRepository extends JpaRepository<Knife, Long> {
    
    List<Knife> findByBrandId(Long brandId);
    
    List<Knife> findByModelId(Long modelId);
    
    Optional<Knife> findByModelIdAndBrandId(Long modelId, Long brandId);
    
    Optional<Knife> findByModelAndBrand(KnifeModel model, Brand brand);
    
    @Query("SELECT k FROM Knife k WHERE k.model.id = :modelId AND k.brand.id = :brandId AND k.photoPath IS NOT NULL")
    Optional<Knife> findCertificateByModelAndBrand(@Param("modelId") Long modelId, @Param("brandId") Long brandId);
    
    @Query("SELECT k FROM Knife k WHERE k.photoPath IS NOT NULL")
    List<Knife> findAllCertificates();
    
    @Query("SELECT k FROM Knife k JOIN k.model m JOIN k.brand b " +
           "WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(k.index) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Knife> searchKnives(@Param("query") String query);
    
    @Query("SELECT DISTINCT k.brand FROM Knife k WHERE k.photoPath IS NOT NULL ORDER BY k.brand.name")
    List<Brand> findAllBrandsWithCertificates();
    
    @Query("SELECT k FROM Knife k WHERE k.brand = :brand AND k.photoPath IS NOT NULL ORDER BY k.model.name")
    List<Knife> findCertificatesByBrand(@Param("brand") Brand brand);
    
    @Query("SELECT k FROM Knife k WHERE k.brand = :brand ORDER BY k.model.name")
    List<Knife> findAllByBrand(@Param("brand") Brand brand);
    
    @Query("SELECT k FROM Knife k WHERE k.model.name = :modelName AND k.brand.name = :brandName AND k.index = :index")
    Optional<Knife> findByModelNameBrandNameAndIndex(@Param("modelName") String modelName, @Param("brandName") String brandName, @Param("index") String index);
}
