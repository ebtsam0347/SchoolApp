package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelSettlementItem;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TravelSettlementItemRepository extends JpaRepository<TravelSettlementItem, Long> {

    List<TravelSettlementItem> findByTravelSettlementId(Long travelSettlementId);

    List<TravelSettlementItem> findByTravelSettlementIdAndSourceType(Long travelSettlementId,
                                                                     TravelSettlementItemSourceType sourceType);

    List<TravelSettlementItem> findBySourceTypeAndSourceRecordId(TravelSettlementItemSourceType sourceType,
                                                                 Long sourceRecordId);

    /**
     * Source-record ids already pulled into a set of settlements — used by
     * {@code prepareFinalSettlement} to skip an expense/advance that a
     * still-active earlier settlement of the same participant already
     * consumed. Callers must pass a non-empty id set.
     */
    List<TravelSettlementItem> findByTravelSettlementIdInAndSourceType(Collection<Long> travelSettlementIds,
                                                                       TravelSettlementItemSourceType sourceType);
}
