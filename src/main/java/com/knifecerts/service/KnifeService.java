package com.knifecerts.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.Brand;
import com.knifecerts.model.Knife;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeRepository;

@Service
@Transactional(readOnly = true)
public class KnifeService {

    private final KnifeRepository knifeRepository;
    private final BrandRepository brandRepository;

    @Autowired
    public KnifeService(KnifeRepository knifeRepository, BrandRepository brandRepository) {
        this.knifeRepository = knifeRepository;
        this.brandRepository = brandRepository;
    }

    public List<Brand> getAllBrandsWithCertificates() {
        return knifeRepository.findAllBrandsWithCertificates();
    }

    public List<Knife> getCertificatesByBrand(String brandName) {
        Optional<Brand> brand = brandRepository.findByName(brandName);
        if (brand.isPresent()) {
            return knifeRepository.findCertificatesByBrand(brand.get());
        }
        return List.of();
    }

    public List<Knife> getAllKnivesByBrand(String brandName) {
        Optional<Brand> brand = brandRepository.findByName(brandName);
        if (brand.isPresent()) {
            return knifeRepository.findAllByBrand(brand.get());
        }
        return List.of();
    }

    public Optional<Knife> findCertificate(String brandName, String modelName) {
        Optional<Brand> brand = brandRepository.findByName(brandName);
        if (brand.isPresent()) {
            List<Knife> knives = knifeRepository.findCertificatesByBrand(brand.get());
            return knives.stream()
                    .filter(knife -> knife.getModel().getName().equals(modelName))
                    .findFirst();
        }
        return Optional.empty();
    }

    public List<Knife> getAlternatives(Long knifeId) {
        Optional<Knife> knife = knifeRepository.findById(knifeId);
        if (knife.isPresent()) {
            return knife.get().getAlternatives().stream()
                    .filter(alt -> alt.getPhotoPath() != null) // Только с сертификатами
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    public List<Knife> getAllAlternatives(Long knifeId) {
        Optional<Knife> knife = knifeRepository.findById(knifeId);
        if (knife.isPresent()) {
            return List.copyOf(knife.get().getAlternatives());
        }
        return List.of();
    }

    public List<Knife> searchKnives(String query) {
        return knifeRepository.searchKnives(query);
    }

    public List<Knife> getAllCertificates() {
        return knifeRepository.findAllCertificates();
    }

    public Optional<Knife> getKnifeById(Long id) {
        return knifeRepository.findById(id);
    }
}