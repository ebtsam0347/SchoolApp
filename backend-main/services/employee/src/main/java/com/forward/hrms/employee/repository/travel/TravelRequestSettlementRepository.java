package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelRequestSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelRequestSettlementRepository extends JpaRepository<TravelRequestSettlement, Long> {

    Optional<TravelRequestSettlement> findBySettlementNumber(String settlementNumber);

    boolean existsBySettlementNumber(String settlementNumber);

    List<TravelRequestSettlement> findByTravelRequestId(Long travelRequestId);

    List<TravelRequestSettlement> findByTravelRequestParticipantId(Long travelRequestParticipantId);
}
