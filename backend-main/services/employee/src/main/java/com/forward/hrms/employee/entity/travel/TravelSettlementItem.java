package com.forward.hrms.employee.entity.travel;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemSourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One financial record (expense, advance or adjustment) included in a
 * {@link TravelRequestSettlement}. Provides a permanent audit trail of
 * exactly which records were used to compute the final settlement.
 * <p>
 * Audit columns come from {@link AuditedEntity}.
 */
@Entity
@Table(name = "travel_settlement_items")
@Getter
@Setter
public class TravelSettlementItem extends AuditedEntity {

    // ========================================================
    // Settlement relationship
    // ========================================================

    /**
     * Settlement to which this item belongs. Stored as a plain
     * {@code Long} FK (project convention); the DB foreign key
     * {@code fk_travel_settlement_items_settlement} lives in the
     * migration.
     */
    @Column(name = "travel_settlement_id", nullable = false)
    private Long travelSettlementId;

    // ========================================================
    // Source record
    // ========================================================

    /**
     * Type of financial record being included: EXPENSE, ADVANCE or
     * ADJUSTMENT.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type_code", nullable = false, length = 30)
    private TravelSettlementItemSourceType sourceType;

    /**
     * ID of the source record. Depending on {@link #sourceType} this is
     * {@code travel_expense_items.id}, {@code travel_request_advances.id}
     * or an approved adjustment reference. Deliberately NOT a mapped
     * association because one column can refer to different entity types.
     */
    @Column(name = "source_record_id", nullable = false)
    private Long sourceRecordId;

    // ========================================================
    // Financial values
    // ========================================================

    /**
     * Original/source amount.
     */
    @Column(name = "source_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal sourceAmount;

    /**
     * Amount actually included in this settlement. May be lower than
     * {@link #sourceAmount}.
     */
    @Column(name = "settlement_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal settlementAmount;

    /**
     * Currency of the settlement item, e.g. PKR.
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    // ========================================================
    // Direction
    // ========================================================

    /**
     * How this item affects the final settlement: PAYABLE (adds amount
     * payable to employee) or RECEIVABLE (adds amount recoverable from
     * employee).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction_code", nullable = false, length = 20)
    private TravelSettlementItemDirection direction;

    // ========================================================
    // Explanation
    // ========================================================

    /**
     * Snapshot/description explaining why this item was included in the
     * settlement.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ========================================================
    // Optimistic locking
    // ========================================================

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
