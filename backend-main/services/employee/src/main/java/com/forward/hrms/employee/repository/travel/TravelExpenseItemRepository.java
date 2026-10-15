package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelExpenseItem;
import com.forward.hrms.employee.enums.travel.TravelExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelExpenseItemRepository extends JpaRepository<TravelExpenseItem, Long> {

    List<TravelExpenseItem> findByTravelRequestId(Long travelRequestId);

    List<TravelExpenseItem> findByTravelRequestParticipantId(Long travelRequestParticipantId);

    List<TravelExpenseItem> findByTravelRequestParticipantIdAndStatus(Long travelRequestParticipantId,
                                                                      TravelExpenseStatus status);

    List<TravelExpenseItem> findByTravelRequestEntitlementId(Long travelRequestEntitlementId);
}
