package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelRequestAdvance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelRequestAdvanceRepository extends JpaRepository<TravelRequestAdvance, Long> {

    Optional<TravelRequestAdvance> findByAdvanceNumber(String advanceNumber);

    boolean existsByAdvanceNumber(String advanceNumber);

    List<TravelRequestAdvance> findByTravelRequestId(Long travelRequestId);

    List<TravelRequestAdvance> findByTravelRequestParticipantId(Long travelRequestParticipantId);
}
