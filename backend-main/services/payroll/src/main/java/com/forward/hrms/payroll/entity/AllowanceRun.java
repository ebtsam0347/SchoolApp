package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.AllowanceRunStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "allowance_run")
@Getter
@Setter
public class AllowanceRun extends AuditedEntity {

    @Column(name = "fiscal_year_code", nullable = false, length = 7)
    private String fiscalYearCode;

    @Column(name = "allowance_month", nullable = false)
    private int allowanceMonth;

    @Column(name = "allowance_year", nullable = false)
    private int allowanceYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AllowanceRunStatus status =  AllowanceRunStatus.DRAFT;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    /** Progress counter the compute / post saga ticks every ~25 rows so the
     *  FE can render "X processed / total_count" while the runner thread
     *  works. Reset to 0 at each kickoff and at {@code /reset}. */
    @Column(name = "progress_count", nullable = false)
    private int progressCount;

    /** Total candidates at saga kickoff — settings count for compute*/
    @Column(name = "total_count", nullable = false)
    private int totalCount;
}
