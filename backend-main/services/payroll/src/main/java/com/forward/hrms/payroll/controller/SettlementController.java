package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.ApprovalRequest;
import com.forward.hrms.payroll.dto.request.CancelSettlementRequest;
import com.forward.hrms.payroll.dto.request.CreateFinalSettlementRequest;
import com.forward.hrms.payroll.dto.request.SettlementPaymentRequest;
import com.forward.hrms.payroll.dto.response.*;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.service.settlements.FinalSettlementListService;
import com.forward.hrms.payroll.service.settlements.FinalSettlementQueryService;
import com.forward.hrms.payroll.service.settlements.SettlementFinalizationService;
import com.forward.hrms.payroll.service.settlements.SettlementServiceNew;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Tag(name = "Final settlement preview",
        description = "Read-only simulation. Distinct from the actual termination payroll run. Requires `payroll.read`.")
@RestController
@RequestMapping("/settlement/employees")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementServiceNew service; // we are using the new service for the new preview API for testing.
    private final SettlementFinalizationService settlementFinalizationService;
    private final FinalSettlementQueryService finalSettlementQueryService;
    private final FinalSettlementListService finalSettlementListService;


    // new API to make things easier, gathered, collection-mode for preview while terminating employee.
    @Operation(summary = "Preview the settlement payslip", description = "Requires `payroll.read`.")
    @GetMapping("/preview/{employeeId}")
    @RequirePermission("payroll.read")
    public SettlementPreviewDto preview(
            @PathVariable("employeeId") Long employeeId,
            @Parameter @RequestParam(required = false) LocalDate lastWorkingDay,
            @Parameter @RequestParam(required = false) SettlementAdjustmentType settlementAdjustmentType,
            @Parameter @RequestParam(required = false) NoticePayPercentage noticePayPercentage,
            @Parameter @RequestParam(required = false) ForcedTerminationDeductionPercentage forcedDeductionPercentage,
            @Parameter(description = "Monthly basic salaries granted as bonus per full fiscal year, as "
                    + "picked by HR on the termination screen (0.5, 1, 1.5, 2, ...). The termination "
                    + "bonus is one fiscal year's accrual multiplied by this rate. Defaults to 2.")
            @RequestParam(required = false) @DecimalMin("0") BigDecimal bonusRate
            ) {

        return service.previewNew(employeeId, lastWorkingDay, settlementAdjustmentType, noticePayPercentage, forcedDeductionPercentage, bonusRate);
    }

    /**
     * ===========================
     * Create New Termination Request
     * ===========================
     */
    @Operation(summary = "Submit employee final settlement")
    @PostMapping
    @RequirePermission("payroll.write")
    @ResponseStatus(HttpStatus.CREATED)
    public FinalSettlementDto create(@Valid @RequestBody CreateFinalSettlementRequest request) {

        return settlementFinalizationService.create(request);
    }

    /**
     * ===========================
     * Details main heading details
     * ===========================
     */
    @Operation(summary = "Get final settlement")
    @GetMapping("settlementId/{settlementId}")
    @RequirePermission("payroll.read")
    public FinalSettlementDto SubmittedSettlement(
            @PathVariable Long settlementId) {

        return settlementFinalizationService.get(settlementId);
    }

    /**
     * ===========================
     * Details headings + subheading details
     * ===========================
     */
    @Operation(summary = "Get final settlement with complete " +
            "sub data details to review already submitted termination application")
    @GetMapping("submitted-settlements/{settlementId}")
    @RequirePermission("payroll.read")
    public FinalSettlementDetailsDto get(
            @PathVariable Long settlementId) {

        return finalSettlementQueryService.get(settlementId);
    }

    /**
     * ===========================
     * List Settlements
     * ===========================
     */
    @Operation(summary = "Search employee final settlements")
    @GetMapping
    @RequirePermission("payroll.read")
    public Page<FinalSettlementListDto> search(

            @ParameterObject
            FinalSettlementSearchRequest request,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC)
            Pageable pageable) {

        return finalSettlementListService.search(request, pageable);
    }

    /**
     * ===========================
     * HR Approval & Confirmation API
     * ===========================
     */
    @Operation(summary = "Approve final settlement — DRAFT → HR_APPROVED",
            description = """
                    HR verification stamp. The financial closing already happened at \
                    creation; this only flips the status.
                    
                    **Blocked by uncleared company belongings.** The employee's belongings are \
                    re-checked live against employee-service on every attempt. While anything is \
                    still `ACTIVE` without an `APPROVED` clearance, the answer is \
                    409 `BELONGING_CLEARANCE_PENDING` and the ProblemDetail carries \
                    `belongings[]`, `pendingCount` and `pendingAssetValue` so the list can be \
                    rendered straight from the error. The same list is on \
                    `GET /settlement/employees/preview/{employeeId}` as `belongings`, so HR sees \
                    it before submitting. An employee holding nothing passes through untouched.
                    
                    Requires `payroll.write`.""")
    @ApiResponse(responseCode = "404", description = "Unknown settlement id")
    @ApiResponse(responseCode = "409", description = "Not `DRAFT` — already approved, paid, rejected "
            + "or cancelled; or `BELONGING_CLEARANCE_PENDING` — uncleared company belongings")
    @ApiResponse(responseCode = "502", description = "`BELONGING_CLEARANCE_CHECK_FAILED` — "
            + "employee-service unreachable, so the belongings could not be verified. The approval "
            + "is refused rather than let through.")
    @PostMapping("/{settlementId}/hr-approval-confirmation")
    @RequirePermission("payroll.write")
    public FinalSettlementDto hrApproval(
            @PathVariable Long settlementId,
            @Valid @RequestBody ApprovalRequest request) {

        return settlementFinalizationService.hrApproval(settlementId, request);
    }


    /**
     * ===========================
     * Rejection
     * ===========================
     */
    @Operation(summary = "Reject final settlement — DRAFT → REJECTED",
            description = "Reverses the leave / bonus / advance closing and tells employee-service "
                    + "to undo the termination. Only a DRAFT can be rejected; after HR approval "
                    + "use cancel. Requires `payroll.write`.")
    @ApiResponse(responseCode = "404", description = "Unknown settlement id")
    @ApiResponse(responseCode = "409", description = "Not `DRAFT` — use cancel instead")
    @PostMapping("/{settlementId}/reject")
    @RequirePermission("payroll.write")
    public FinalSettlementDto reject(
            @PathVariable Long settlementId,
            @Valid @RequestBody ApprovalRequest request) {

        return settlementFinalizationService.reject(settlementId, request);
    }

    /**
     * ===========================
     * Payments & Accounts Approval
     * ===========================
     */
    @Operation(summary = "Mark settlement as paid — HR_APPROVED → PAID",
            description = "Accounts records the actual payment (date / method / reference) and "
                    + "releases it. Requires `payroll.settlement.accounts`, which HR deliberately "
                    + "does not hold — `payroll.write` alone will not do, or HR would hold both "
                    + "halves of the approve/pay split. Same split as `payroll.offcycle.accounts`.")
    @ApiResponse(responseCode = "404", description = "Unknown settlement id")
    @ApiResponse(responseCode = "409", description = "Not `HR_APPROVED` — still draft, or already paid / rejected / cancelled")
    @PostMapping("/{settlementId}/pay")
    @RequirePermission("payroll.settlement.accounts")
    public FinalSettlementDto markPaid(
            @PathVariable Long settlementId,
            @Valid @RequestBody SettlementPaymentRequest request) {

        return settlementFinalizationService.markPaid(settlementId, request);
    }

    /**
     * ===========================
     * Cancel
     * ===========================
     */
    @Operation(summary = "Cancel settlement — DRAFT / HR_APPROVED → CANCELLED",
            description = "Reverses the closing and the termination, same side effects as reject. "
                    + "HR only: Accounts is the payment authority, not a second approval layer. "
                    + "Requires `payroll.write`.")
    @ApiResponse(responseCode = "404", description = "Unknown settlement id")
    @ApiResponse(responseCode = "409", description = "Already `PAID`, `REJECTED` or `CANCELLED`")
    @PostMapping("/{settlementId}/cancel")
    @RequirePermission("payroll.write")
    public FinalSettlementDto cancel(
            @PathVariable Long settlementId,
            @Valid @RequestBody CancelSettlementRequest request) {

        return settlementFinalizationService.cancel(settlementId, request);
    }

}
