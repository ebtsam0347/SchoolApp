package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelRequestLeg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelRequestLegRepository extends JpaRepository<TravelRequestLeg, Long> {

    List<TravelRequestLeg> findByTravelRequestIdOrderByLegSequenceAsc(Long travelRequestId);

    boolean existsByTravelRequestIdAndLegSequence(Long travelRequestId, Integer legSequence);

    void deleteByTravelRequestId(Long travelRequestId);
}
