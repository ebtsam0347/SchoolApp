package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.belonging.ActivationStatus;
import com.forward.hrms.employee.enums.belonging.BelongingCondition;
import com.forward.hrms.employee.enums.belonging.ClearanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Whether an employee is clear of company belongings, and exactly which
 * ones still block them.
 *
 * <p>Owned by employee-service because the rule is a belongings rule, not a
 * payroll rule: payroll-service asks for the verdict and never
 * re-implements it. Consumed twice — once by the settlement preview so HR
 * sees the list before submitting, and again by
 * {@code POST /settlement/employees/{id}/hr-approval-confirmation}, which
 * refuses while {@code cleared} is false.
 *
 * <p>Lives in {@code api} rather than employee-service so payroll gets the
 * typed shape instead of a redeclared view record.
 */
@Schema(name = "BelongingClearanceCheckDto",
        description = "Company belongings still held by an employee. `cleared` is the gate the "
                + "final-settlement HR approval reads; `pending` is the list HR has to act on.")
public record BelongingClearanceCheckDto(

        @Schema(description = "Employee the check ran for.")
        Long employeeId,

        @Schema(description = "True when nothing blocks — either the employee was never issued "
                + "anything, or every issuance is out of their hands (RETURNED / LOST / DAMAGED / "
                + "INACTIVE) or has an APPROVED clearance sign-off.")
        boolean cleared,

        @Schema(description = "Size of `pending`. Zero exactly when `cleared` is true.")
        int pendingCount,

        @Schema(description = "Sum of `assetPrice` over `pending`, for the figure HR quotes when "
                + "chasing the assets. Null prices count as zero; this is context only and is "
                + "never deducted from the settlement.")
        BigDecimal pendingAssetValue,

        @Schema(description = "The blocking issuance, oldest issuance first.")
        List<PendingBelonging> pending
) {

    /** Nothing held — the common case for most terminations. */
    public static BelongingClearanceCheckDto cleared(Long employeeId) {
        return new BelongingClearanceCheckDto(employeeId, true, 0, BigDecimal.ZERO, List.of());
    }

    @Schema(name = "BelongingClearanceCheckDto.PendingBelonging",
            description = "One belonging still charged to the employee. `activationStatus` and "
                    + "`clearanceStatus` are both carried so HR can see why the row blocks: it is "
                    + "ACTIVE and its clearance is not APPROVED.")
    public record PendingBelonging(

            @Schema(description = "belonging_issuance.id — the row to clear via "
                    + "`PUT /belonging-issuance/{id}/clearance`.")
            Long issuanceId,

            @Schema(description = "Catalog id from belonging_master.")
            Long belongingMasterId,

            @Schema(description = "Catalog name — \"Laptop\", \"Motorcycle\", \"SIM\".")
            String belongingName,

            @Schema(description = "Free-text model as captured at issuance.")
            String assetModel,

            @Schema(description = "Model year.")
            Short assetYear,

            @Schema(description = "Book price of the asset, in rupees.")
            BigDecimal assetPrice,

            @Schema(description = "When it was handed over.")
            LocalDate issuedDate,

            @Schema(description = "When it was due back, if an expiry was set.")
            LocalDate expiryDate,

            @Schema(description = "Condition at issuance.")
            BelongingCondition condition,

            @Schema(description = "Always ACTIVE on a blocking row — anything else is out of the "
                    + "employee's hands and does not block.")
            ActivationStatus activationStatus,

            @Schema(description = "Null, PENDING or REJECTED on a blocking row. APPROVED clears it.")
            ClearanceStatus clearanceStatus,

            @Schema(description = "Clearance reference, once one has been recorded.")
            String clearanceId,

            @Schema(description = "Clearance date, once one has been recorded.")
            LocalDate clearanceDate,

            @Schema(description = "Issuance notes.")
            String description
    ) {}
}
