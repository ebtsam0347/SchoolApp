package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelRequest;
import com.forward.hrms.employee.enums.travel.TravelRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TravelRequestRepository extends JpaRepository<TravelRequest, Long> {

    Optional<TravelRequest> findByRequestNo(String requestNo);

    boolean existsByRequestNo(String requestNo);

    Page<TravelRequest> findByRequesterEmployeeId(Long requesterEmployeeId, Pageable pageable);

    @Query("""
            select r from TravelRequest r
            where (:status is null or r.status = :status)
              and (:travelTypeCode is null or r.travelTypeCode = :travelTypeCode)
              and (:requesterEmployeeId is null or r.requesterEmployeeId = :requesterEmployeeId)
              and (:policyVersionId is null or r.policyVersionId = :policyVersionId)
            order by r.createdAt desc
            """)
    Page<TravelRequest> search(TravelRequestStatus status,
                               String travelTypeCode,
                               Long requesterEmployeeId,
                               Long policyVersionId,
                               Pageable pageable);
}
