package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeRequisition;
import com.forward.hrms.employee.enums.RequisitionStatus;
import com.forward.hrms.employee.enums.RequisitionType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRequisitionRepository extends JpaRepository<EmployeeRequisition, Long> {

    @Query("""
            select r from EmployeeRequisition r
            where (:status is null or r.status = :status)
              and (:type is null or r.type = :type)
              and (:departmentId is null or r.departmentId = :departmentId)
            order by r.createdAt desc
            """)
    Page<EmployeeRequisition> search(RequisitionStatus status,
                                     RequisitionType type,
                                     Long departmentId,
                                     Pageable pageable);

    @Query("""
            select r from EmployeeRequisition r
            where (:status is null or r.status = :status)
              and (:type is null or r.type = :type)
              and (
                             r.departmentId = :callerDeptId
                             or r.requestedByUserId = :currentUserId
                         )
            order by r.createdAt desc
            """)
    Page<EmployeeRequisition> getRequisitionListSelfOrWithInDepartment(RequisitionStatus status,
                                     RequisitionType type,
                                     Long callerDeptId,
                                     Long currentUserId,
                                     Pageable pageable);

    @Query("""
            select r from EmployeeRequisition r
            where (:status is null or r.status = :status)
              and (:type is null or r.type = :type)
              and (:departmentId is null or r.departmentId = :departmentId)
              and (:id is null or r.id = :id)
              and (:jobDescription is null or r.jobDescription like concat('%', :jobDescription, '%'))
            """)
    Page<EmployeeRequisition> hrListRequisition(RequisitionStatus status,
                                                RequisitionType type,
                                                Long departmentId,
                                                Long id,
                                                String jobDescription,
                                                Pageable pageable);
}