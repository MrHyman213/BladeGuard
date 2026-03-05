package com.knifecerts;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.Brand;
import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionStatus;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.SubmissionRepository;

@Service
@Transactional
public class BrandService {
    
    private static final Logger logger = Logger.getLogger(BrandService.class.getName());
    
    private final BrandRepository brandRepository;
    private final SubmissionRepository submissionRepository;
    
    public BrandService(BrandRepository brandRepository, SubmissionRepository submissionRepository) {
        this.brandRepository = brandRepository;
        this.submissionRepository = submissionRepository;
    }
    
    public List<Brand> getAllBrands() {
        logger.info("Getting all brands");
        return brandRepository.findAll();
    }
    
    /**
     * Получить только бренды, у которых есть хотя бы один одобренный нож.
     * Используется для отображения пользователям.
     */
    public List<Brand> getBrandsWithApprovedKnives() {
        logger.info("Getting brands with approved knives");
        return brandRepository.findAll().stream()
            .filter(brand -> {
                List<Submission> approved = submissionRepository.findByStatusAndBrand(SubmissionStatus.APPROVED, brand);
                return !approved.isEmpty();
            })
            .collect(Collectors.toList());
    }
    
    public List<Brand> searchBrands(String query) {
        logger.info("Searching brands with query: " + query);
        if (query == null || query.trim().isEmpty()) {
            return getBrandsWithApprovedKnives();
        }
        return getBrandsWithApprovedKnives().stream()
            .filter(brand -> brand.getName().toLowerCase().contains(query.toLowerCase()))
            .collect(Collectors.toList());
    }
    
    public Brand findOrCreateBrand(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "Бренд не указан";
        }
        
        String normalizedName = name.trim();
        Optional<Brand> existing = brandRepository.findByName(normalizedName);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        Brand newBrand = new Brand();
        newBrand.setName(normalizedName);
        return brandRepository.save(newBrand);
    }
    
    public List<Submission> getUnbrandedKnives() {
        logger.info("Getting unbranded knives");
        Brand unbrandedBrand = brandRepository.findByName("Бренд не указан").orElse(null);
        if (unbrandedBrand == null) {
            return List.of();
        }
        return submissionRepository.findByStatusAndBrand(SubmissionStatus.APPROVED, unbrandedBrand);
    }
    
    public boolean hasUnbrandedKnives() {
        return !getUnbrandedKnives().isEmpty();
    }
}
