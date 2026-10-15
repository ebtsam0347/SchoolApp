package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeDocument;
import com.forward.hrms.employee.enums.DocumentType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

    List<EmployeeDocument> findAllByEmployeeIdOrderByIdDesc(Long employeeId);

    List<EmployeeDocument> findAllByEmployeeIdAndDocTypeOrderByIdDesc(Long employeeId, DocumentType docType);

    Page<EmployeeDocument> findByEmployeeId(Long employeeId, Pageable pageable);
}
