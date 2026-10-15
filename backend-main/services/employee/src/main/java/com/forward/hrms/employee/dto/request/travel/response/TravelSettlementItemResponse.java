package com.forward.hrms.employee.dto.request.travel.response;

import com.forward.hrms.employee.entity.travel.TravelSettlementItem;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemSourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response representation of a Travel Settlement Item - the financial
 * record that was actually included in a Travel Request Settlement.
 * Relationships are represented using reference IDs rather than JPA
 * entities.
 */
public record TravelSettlementItemResponse(

        /* Unique database identifier of the settlement item. */
        Long id,

        /* ID of the parent Travel Request Settlement. */
        Long settlementId,

        /* Type of financial record: EXPENSE, ADVANCE, ADJUSTMENT. */
        TravelSettlementItemSourceType sourceType,

        /* ID of the source financial record (interpretation depends on sourceType). */
        Long sourceRecordId,

        /* Original/source amount. */
        BigDecimal sourceAmount,

        /* Amount included in the settlement. */
        BigDecimal settlementAmount,

        /* Currency of this settlement item. */
        String currencyCode,

        /*
         * Financial direction: PAYABLE contributes toward money payable to
         * the employee; RECEIVABLE contributes toward money recoverable
         * from the employee.
         */
        TravelSettlementItemDirection direction,

        /* Explanation describing this settlement item. */
        String description,

        /* Timestamp when the settlement item was created. */
        LocalDateTime createdAt,

        /* Username that created the settlement item. */
        String createdBy,

        /* Optimistic-locking version of the settlement item. */
        Long version
) {

    public static TravelSettlementItemResponse of(TravelSettlementItem e) {
        return new TravelSettlementItemResponse(
                e.getId(), e.getTravelSettlementId(), e.getSourceType(), e.getSourceRecordId(),
                e.getSourceAmount(), e.getSettlementAmount(), e.getCurrencyCode(),
                e.getDirection(), e.getDescription(),
                e.getCreatedAt(), e.getCreatedBy(), e.getVersion());
    }
}
