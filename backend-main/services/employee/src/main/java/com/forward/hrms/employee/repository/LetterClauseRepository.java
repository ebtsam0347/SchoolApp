package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.LetterClause;
import com.forward.hrms.employee.enums.LetterType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LetterClauseRepository extends JpaRepository<LetterClause, Long> {

    List<LetterClause> findAllByLetterTypeAndActiveTrueOrderBySectionAscSeqAsc(LetterType letterType);
}
