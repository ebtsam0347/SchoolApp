package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelRequestEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TravelRequestEntitlementRepository extends JpaRepository<TravelRequestEntitlement, Long> {

    List<TravelRequestEntitlement> findByTravelRequestId(Long travelRequestId);

    void deleteByTravelRequestId(Long travelRequestId);

    List<TravelRequestEntitlement> findByTravelRequestParticipantId(Long travelRequestParticipantId);

    @Query("""
            select e from TravelRequestEntitlement e
            where e.travelRequestParticipantId = :participantId
              and e.active = true
            """)
    List<TravelRequestEntitlement> findActiveByParticipantId(Long participantId);

    @Query("""
            select e from TravelRequestEntitlement e
            where e.travelRequestParticipantId = :participantId
              and e.entitlementTypeCode = :entitlementTypeCode
              and e.active = true
            """)
    List<TravelRequestEntitlement> findActiveByParticipantIdAndType(Long participantId, String entitlementTypeCode);
}
