package com.knifecerts.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.Knife;
import com.knifecerts.repository.KnifeRepository;

/**
 * Сервис для поиска транзитивных альтернатив.
 * 
 * Транзитивная альтернатива - модель, связанная с альтернативой данного ножа,
 * но не связанная с ним напрямую.
 * 
 * Алгоритм:
 * 1. Собрать все прямые альтернативы ножа + newAlternativeIds → множество direct
 * 2. Для каждой модели из direct собрать её альтернативы → множество candidates
 * 3. Вернуть candidates \ direct \ {knifeId}
 * 
 * Требования: 9.1, 9.6, 17.1-17.4
 */
@Service
@Transactional(readOnly = true)
public class TransitiveAlternativesService {
    
    @Autowired
    private KnifeRepository knifeRepository;
    
    /**
     * Найти транзитивные альтернативы для ножа.
     * 
     * @param knifeId ID ножа
     * @param newAlternativeIds Набор ID новых альтернатив, которые будут добавлены
     * @return Множество транзитивных альтернатив (ножей)
     */
    public Set<Knife> findTransitive(Long knifeId, Set<Long> newAlternativeIds) {
        Optional<Knife> knifeOpt = knifeRepository.findById(knifeId);
        if (knifeOpt.isEmpty()) {
            return new HashSet<>();
        }
        
        Knife knife = knifeOpt.get();
        
        // Шаг 1: Собрать все прямые альтернативы + новые альтернативы
        Set<Long> direct = new HashSet<>();
        
        // Добавляем существующие прямые альтернативы
        for (Knife alt : knife.getAlternatives()) {
            direct.add(alt.getId());
        }
        
        // Добавляем новые альтернативы
        direct.addAll(newAlternativeIds);
        
        // Шаг 2: Для каждой модели из direct собрать её альтернативы
        Set<Long> candidates = new HashSet<>();
        for (Long altKnifeId : direct) {
            Optional<Knife> altKnifeOpt = knifeRepository.findById(altKnifeId);
            if (altKnifeOpt.isPresent()) {
                Knife altKnife = altKnifeOpt.get();
                for (Knife transitiveAlt : altKnife.getAlternatives()) {
                    candidates.add(transitiveAlt.getId());
                }
            }
        }
        
        // Шаг 3: Вернуть candidates \ direct \ {knifeId}
        candidates.removeAll(direct);
        candidates.remove(knifeId);
        
        // Преобразовать ID в объекты Knife
        return candidates.stream()
            .map(id -> knifeRepository.findById(id))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }
}
