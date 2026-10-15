package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelRequestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TravelRequestParticipantRepository extends JpaRepository<TravelRequestParticipant, Long> {

    List<TravelRequestParticipant> findByTravelRequestId(Long travelRequestId);

    /** Matches the {@code uq_travel_request_participants_employee} unique key. */
    Optional<TravelRequestParticipant> findByTravelRequestIdAndEmployeeId(Long travelRequestId, Long employeeId);

    boolean existsByTravelRequestIdAndEmployeeId(Long travelRequestId, Long employeeId);

    /** Wipe every participant of a request — used when the creator re-edits a DRAFT. */
    void deleteByTravelRequestId(Long travelRequestId);

    @Query("""
            select p from TravelRequestParticipant p
            where p.travelRequestId = :travelRequestId
              and p.travelling = true
            """)
    List<TravelRequestParticipant> findTravellingByTravelRequestId(Long travelRequestId);

    List<TravelRequestParticipant> findByEmployeeId(Long employeeId);
}
