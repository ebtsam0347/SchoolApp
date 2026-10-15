package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.LetterSettings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LetterSettingsRepository extends JpaRepository<LetterSettings, Long> {

    Optional<LetterSettings> findFirstByOrderByIdAsc();
}
