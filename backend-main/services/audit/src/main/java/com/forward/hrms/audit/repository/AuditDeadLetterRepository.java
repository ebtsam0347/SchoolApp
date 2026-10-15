package com.forward.hrms.audit.repository;

import com.forward.hrms.audit.entity.AuditDeadLetter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditDeadLetterRepository
        extends JpaRepository<AuditDeadLetter, Long>, JpaSpecificationExecutor<AuditDeadLetter> {
}
