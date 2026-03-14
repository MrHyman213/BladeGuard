package com.knifecerts.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.Brand;
import com.knifecerts.model.BufferAlternative;
import com.knifecerts.model.Knife;
import com.knifecerts.model.KnifeModel;
import com.knifecerts.model.SubmissionBuffer;
import com.knifecerts.model.SubmissionStatus;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeModelRepository;
import com.knifecerts.repository.KnifeRepository;
import com.knifecerts.repository.SubmissionBufferRepository;

@Service
@Transactional
public class SubmissionBufferService {

    private final SubmissionBufferRepository submissionBufferRepository;
    private final BrandRepository brandRepository;
    private final KnifeModelRepository knifeModelRepository;
    private final KnifeRepository knifeRepository;

    @Autowired
    public SubmissionBufferService(
            SubmissionBufferRepository submissionBufferRepository,
            BrandRepository brandRepository,
            KnifeModelRepository knifeModelRepository,
            KnifeRepository knifeRepository) {
        this.submissionBufferRepository = submissionBufferRepository;
        this.brandRepository = brandRepository;
        this.knifeModelRepository = knifeModelRepository;
        this.knifeRepository = knifeRepository;
    }

    public SubmissionBuffer createSubmission(Long userId, String username, String modelName, 
                                           String brandName, String index, String photoPath) {
        SubmissionBuffer submission = new SubmissionBuffer(userId, username, modelName, brandName, photoPath);
        submission.setIndex(index);
        return submissionBufferRepository.save(submission);
    }

    public List<SubmissionBuffer> getAllPendingSubmissions() {
        return submissionBufferRepository.findAllOrderByCreatedAt();
    }

    public Optional<SubmissionBuffer> getSubmissionById(Long id) {
        return submissionBufferRepository.findById(id);
    }

    public SubmissionBuffer getSubmissionByIdWithAlternatives(Long id) {
        return submissionBufferRepository.findByIdWithAlternatives(id)
            .orElseThrow(() -> new RuntimeException("Submission not found: " + id));
    }

    public List<SubmissionBuffer> getPendingSubmissionsWithAlternatives() {
        return submissionBufferRepository.findByStatusWithAlternatives(SubmissionStatus.PENDING);
    }

    public List<SubmissionBuffer> getSubmissionsByUserId(Long userId) {
        return submissionBufferRepository.findByUserId(userId);
    }

    public void addAlternativeToSubmission(Long submissionId, String modelName, String brandName) {
        Optional<SubmissionBuffer> submissionOpt = submissionBufferRepository.findById(submissionId);
        if (submissionOpt.isPresent()) {
            SubmissionBuffer submission = submissionOpt.get();
            BufferAlternative alternative = new BufferAlternative(modelName, brandName);
            submission.addAlternative(alternative);
            submissionBufferRepository.save(submission);
        }
    }

    public void removeAlternativeFromSubmission(Long submissionId, String modelName, String brandName) {
        Optional<SubmissionBuffer> submissionOpt = submissionBufferRepository.findById(submissionId);
        if (submissionOpt.isPresent()) {
            SubmissionBuffer submission = submissionOpt.get();
            submission.getAlternatives().removeIf(alt -> 
                alt.getModelName().equals(modelName) && 
                (alt.getBrandName() == null || alt.getBrandName().equals(brandName))
            );
            submissionBufferRepository.save(submission);
        }
    }

    @Transactional
    public Knife approveSubmission(Long submissionId) {
        Optional<SubmissionBuffer> submissionOpt = submissionBufferRepository.findById(submissionId);
        if (!submissionOpt.isPresent()) {
            throw new RuntimeException("Submission not found: " + submissionId);
        }

        SubmissionBuffer submission = submissionOpt.get();
        
        // Создаем или находим бренд
        Brand brand = findOrCreateBrand(submission.getBrandName());
        
        // Создаем или находим модель
        KnifeModel model = findOrCreateKnifeModel(submission.getModelName());
        
        // Создаем основной нож
        Knife knife = new Knife(model, brand, submission.getIndex(), submission.getPhotoPath());
        knife = knifeRepository.save(knife);
        
        // Обрабатываем альтернативы
        for (BufferAlternative bufferAlt : submission.getAlternatives()) {
            Brand altBrand = findOrCreateBrand(bufferAlt.getBrandName());
            KnifeModel altModel = findOrCreateKnifeModel(bufferAlt.getModelName());
            
            // Проверяем, существует ли уже такой нож
            Optional<Knife> existingKnife = knifeRepository.findByModelAndBrand(altModel, altBrand);
            Knife alternativeKnife;
            
            if (existingKnife.isPresent()) {
                alternativeKnife = existingKnife.get();
            } else {
                // Создаем альтернативный нож без фото (только как альтернатива)
                alternativeKnife = new Knife(altModel, altBrand, null, null);
                alternativeKnife = knifeRepository.save(alternativeKnife);
            }
            
            // Добавляем связь альтернативы
            knife.addAlternative(alternativeKnife);
        }
        
        knifeRepository.save(knife);
        
        // Удаляем заявку из буфера
        submissionBufferRepository.delete(submission);
        
        return knife;
    }

    public void rejectSubmission(Long submissionId) {
        submissionBufferRepository.deleteById(submissionId);
    }

    private Brand findOrCreateBrand(String name) {
        return brandRepository.findByName(name)
                .orElseGet(() -> brandRepository.save(new Brand(name)));
    }

    private KnifeModel findOrCreateKnifeModel(String name) {
        return knifeModelRepository.findByName(name)
                .orElseGet(() -> knifeModelRepository.save(new KnifeModel(name)));
    }
}