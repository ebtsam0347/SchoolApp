package com.forward.hrms.payroll.entity;


import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employee_final_settlements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeFinalSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * ------------------------------------------------------------------------
     * Employee Snapshot
     * ------------------------------------------------------------------------
     */

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_code", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "employee_name", nullable = false, length = 150)
    private String employeeName;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String section;

    @Column(length = 100)
    private String designation;

    @Column(length = 30)
    private String grade;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_type", length = 30)
    private GradeType gradeType;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "gratuity_start_date")
    private LocalDate gratuityStartDate;

    @Column(name = "last_working_day", nullable = false)
    private LocalDate lastWorkingDay;

    /*
     * ------------------------------------------------------------------------
     * Salary Snapshot
     * ------------------------------------------------------------------------
     */

    @Column(name = "monthly_basic_salary", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyBasicSalary;

    @Column(name = "earned_basic_salary", nullable = false, precision = 18, scale = 2)
    private BigDecimal earnedBasicSalary;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal allowances;

    @Column(name = "gross_salary", nullable = false, precision = 18, scale = 2)
    private BigDecimal grossSalary;

    /*
     * ------------------------------------------------------------------------
     * Gratuity
     * ------------------------------------------------------------------------
     */

    @Column(name = "gratuity_eligible", nullable = false)
    private Boolean gratuityEligible;

    @Column(name = "gratuity_completed_years", nullable = false)
    private Integer gratuityCompletedYears;

    @Column(name = "gratuity_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal gratuityAmount;

    /*
     * ------------------------------------------------------------------------
     * Advances
     * ------------------------------------------------------------------------
     */

    @Column(name = "permanent_advance", nullable = false, precision = 18, scale = 2)
    private BigDecimal permanentAdvance;

    @Column(name = "short_term_advance", nullable = false, precision = 18, scale = 2)
    private BigDecimal shortTermAdvance;

    /** Outstanding RFD advances recovered by this settlement (Audit Finding 6). */
    @Column(name = "rfd_advance", nullable = false, precision = 18, scale = 2)
    private BigDecimal rfdAdvance = BigDecimal.ZERO;

    @Column(name = "total_advance", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAdvance;

    /*
     * ------------------------------------------------------------------------
     * Bonus
     * ------------------------------------------------------------------------
     */

    @Column(name = "outstanding_bonus", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingBonus;

    /*
     * ------------------------------------------------------------------------
     * Fair Price Shop
     * ------------------------------------------------------------------------
     */

    @Column(name = "fair_price_shop_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal fairPriceShopAmount;

    /*
     * ------------------------------------------------------------------------
     * Canteen
     * ------------------------------------------------------------------------
     */

    @Column(name = "canteen_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal canteenAmount = BigDecimal.ZERO;

    /*
     * ------------------------------------------------------------------------
     * Excessive Leave (annual tracked separately; medical/casual combined)
     * ------------------------------------------------------------------------
     */

    @Column(name = "annual_excessive_leave_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal annualExcessiveLeaveDays = BigDecimal.ZERO;

    @Column(name = "annual_excessive_leave_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal annualExcessiveLeaveAmount = BigDecimal.ZERO;

    @Column(name = "medical_casual_excessive_leave_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal medicalCasualExcessiveLeaveDays = BigDecimal.ZERO;

    @Column(name = "medical_casual_excessive_leave_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal medicalCasualExcessiveLeaveAmount = BigDecimal.ZERO;

    /*
     * ------------------------------------------------------------------------
     * Totals
     * ------------------------------------------------------------------------
     */

    @Column(name = "gross_settlement", nullable = false, precision = 18, scale = 2)
    private BigDecimal grossSettlement;

    @Column(name = "total_deductions", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "net_settlement", nullable = false, precision = 18, scale = 2)
    private BigDecimal netSettlement;

    /*
     * ------------------------------------------------------------------------
     * Remarks
     * ------------------------------------------------------------------------
     */

    @Column(columnDefinition = "TEXT")
    private String remarks;

    /*
     * ------------------------------------------------------------------------
     * Workflow
     * ------------------------------------------------------------------------
     */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmployeeFinalSettlementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false)
    private SettlementAdjustmentType adjustmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_pay_percentage", length = 30, nullable = false)
    private NoticePayPercentage noticePayPercentage;

    @Column(name = "notice_pay_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal noticePayAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "forced_deduction_percentage")
    private ForcedTerminationDeductionPercentage forcedDeductionPercentage;

    @Column(name = "forced_deduction_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal forcedDeductionAmount = BigDecimal.ZERO;


    /*
     * ------------------------------------------------------------------------
     * HR Audit
     * ------------------------------------------------------------------------
     */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    /*
     * --------------------------------------------------------
     * Payments Audit Fields
     * --------------------------------------------------------
     */
    @Column(name = "paid_by")
    private Long paidBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /*
     * ------------------------------------------------------------------------
     * Child Collections
     * ------------------------------------------------------------------------
     */

    @Builder.Default
    @OneToMany(
            mappedBy = "settlement",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<EmployeeFinalSettlementBonusDetail> bonuses = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "settlement",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<EmployeeFinalSettlementLeaveDetail> leaveDetails = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "settlement",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<EmployeeFinalSettlementDeduction> deductions = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "settlement",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<EmployeeFinalSettlementAdvanceDetail> advanceDetails = new ArrayList<>();

    /*
     * ------------------------------------------------------------------------
     * Attendance / Normal Overtime Snapshot (historical — see §12 in the
     * settlement change log: not a live reference to attendance/overtime
     * transaction tables, just the values HR actually approved).
     * ------------------------------------------------------------------------
     */

    @Builder.Default
    @OneToMany(
            mappedBy = "settlement",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<EmployeeFinalSettlementAttendance> attendanceDetails = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "settlement",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<EmployeeFinalSettlementOvertime> overtimeDetails = new ArrayList<>();

    /*
     * ------------------------------------------------------------------------
     * Audit Hooks
     * ------------------------------------------------------------------------
     */

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /*
     * ------------------------------------------------------------------------
     * Helper Methods
     * ------------------------------------------------------------------------
     */

    public void addBonus(EmployeeFinalSettlementBonusDetail bonus) {
        bonuses.add(bonus);
        bonus.setSettlement(this);
    }

    public void addLeaveDetail(EmployeeFinalSettlementLeaveDetail leave) {
        leaveDetails.add(leave);
        leave.setSettlement(this);
    }

    public void addDeduction(EmployeeFinalSettlementDeduction deduction) {
        deductions.add(deduction);
        deduction.setSettlement(this);
    }

    public void addAdvanceDetail(EmployeeFinalSettlementAdvanceDetail detail) {
        advanceDetails.add(detail);
        detail.setSettlement(this);
    }

    public void addAttendanceDetail(EmployeeFinalSettlementAttendance detail) {
        attendanceDetails.add(detail);
        detail.setSettlement(this);
    }

    public void addOvertimeDetail(EmployeeFinalSettlementOvertime detail) {
        overtimeDetails.add(detail);
        detail.setSettlement(this);
    }
}
