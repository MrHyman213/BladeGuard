package com.knifecerts.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.UserMainMenu;

@Repository
public interface UserMainMenuRepository extends JpaRepository<UserMainMenu, Long> {
    
    @Override
    List<UserMainMenu> findAll();
}
