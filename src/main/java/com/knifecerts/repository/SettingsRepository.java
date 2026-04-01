package com.knifecerts.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.Settings;

@Repository
public interface SettingsRepository extends JpaRepository<Settings, String> {

    Optional<Settings> findByKey(String key);

    boolean existsByKey(String key);
}
