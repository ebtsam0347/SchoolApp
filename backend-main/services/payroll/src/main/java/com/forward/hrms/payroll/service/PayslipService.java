package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.config.PayrollProperties;
import com.forward.hrms.payroll.dto.request.HoldPayslipRequest;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.PayslipLineDto;
import com.forward.hrms.payroll.dto.response.PdfUrlResponse;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.dto.response.PayslipDtoMapper;
import com.forward.hrms.payroll.dto.response.PayslipLineDtoMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PayslipService {

    private final PayslipRepository repo;
    private final PayslipLineRepository lineRepo;
    private final PayrollRunRepository runRepo;
    private final ObjectStorage storage;
    private final PayrollProperties props;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final EmployeeClient employeeClient;

    @Transactional(readOnly = true)
    public PayslipDto get(long id) {
        Payslip p = load(id);
        if (accessFor(p.getEmployeeId()) == Access.RESTRICTED && p.getStatus() == PayslipStatus.DRAFT) {
            // Self / manager callers don't see a DRAFT (run not yet posted);
            // 404 rather than 403 so we don't reveal it exists.
            throw new NotFoundError(PayrollErrorCode.PAYSLIP_NOT_FOUND, "payslip not found");
        }
        List<PayslipLineDto> lines = lineRepo.findAllByPayslipIdOrderByDisplayOrder(p.getId())
                .stream().map(PayslipLineDtoMapper::of).toList();
        return PayslipDtoMapper.of(p, lines);
    }

    @Transactional(readOnly = true)
    public PageResponse<PayslipDto> listForEmployeeYear(long employeeId, int year, Pageable pageable) {
        boolean full = accessFor(employeeId) == Access.FULL;
        Pageable effective = PageableDefaults.apply(pageable, "periodMonth");
        // Self / manager callers must not see DRAFT (run not yet posted).
        // payroll.read (HR / ADMIN / ACCOUNTS) sees everything.
        var page = full
                ? repo.findAllByEmployeeIdAndPeriodYear(employeeId, year, effective)
                : repo.findAllByEmployeeIdAndPeriodYearAndStatusNot(
                        employeeId, year, PayslipStatus.DRAFT, effective);
        return PageResponse.from(page.map(PayslipDtoMapper::of));
    }

    /**
     * Manager-team view: the calling manager's direct reports' payslips for
     * a year (and optional month). Gated on {@code payroll.read.team}
     * (managers) or {@code payroll.read} (HR / ADMIN / ACCOUNTS). Scope is
     * the caller's own direct reports, resolved server-side via
     * {@code /employees/me/team} — holding the permission alone reads
     * nobody. DRAFT is always hidden (the run isn't final). A caller with
     * no reports gets an empty page.
     */
    @Transactional(readOnly = true)
    public PageResponse<PayslipDto> listForTeam(int year, Integer month, Pageable pageable) {
        CurrentUser caller = CurrentUserHolder.get().orElse(null);
        if (caller == null || !(caller.hasPermission("payroll.read.team")
                || caller.hasPermission("payroll.read") || caller.hasPermission("payroll.admin"))) {
            throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                    "Missing required permission: payroll.read.team");
        }
        Pageable effective = PageableDefaults.apply(pageable, "periodMonth");
        Set<Long> reportIds = employeeClient.myDirectReportIds();
        if (reportIds.isEmpty()) {
            return PageResponse.from(Page.<Payslip>empty(effective).map(PayslipDtoMapper::of));
        }
        var page = month != null
                ? repo.findAllByEmployeeIdInAndPeriodYearAndPeriodMonthAndStatusNot(
                        reportIds, year, month, PayslipStatus.DRAFT, effective)
                : repo.findAllByEmployeeIdInAndPeriodYearAndStatusNot(
                        reportIds, year, PayslipStatus.DRAFT, effective);
        return PageResponse.from(page.map(PayslipDtoMapper::of));
    }

    private enum Access { FULL, RESTRICTED }

    /**
     * Authorizes a read of {@code employeeId}'s payslip(s) and returns the
     * visibility level, or throws 403. {@code payroll.read} /
     * {@code payroll.admin} (HR / ADMIN / ACCOUNTS) → {@link Access#FULL}:
     * every status incl. DRAFT. The employee themselves (resolved via
     * employee-service {@code /me}), or a manager holding
     * {@code payroll.read.team} for whom {@code employeeId} is a direct
     * report → {@link Access#RESTRICTED}: posted payslips only, DRAFT
     * hidden. Anything else → 403. Mirrors V0008's self-serve policy
     * (isAuthenticated() + caller == owner), extended with the team scope.
     */
    private Access accessFor(long employeeId) {
        CurrentUser caller = CurrentUserHolder.get().orElse(null);
        if (caller == null) {
            throw new ForbiddenError(CommonErrorCode.FORBIDDEN, "authentication required");
        }
        if (caller.hasPermission("payroll.read") || caller.hasPermission("payroll.admin")) {
            return Access.FULL;
        }
        Long callerEmployeeId = employeeClient.meEmployeeIdOrNull();
        if (callerEmployeeId != null && callerEmployeeId == employeeId) return Access.RESTRICTED;
        if (caller.hasPermission("payroll.read.team")
                && employeeClient.myDirectReportIds().contains(employeeId)) {
            return Access.RESTRICTED;
        }
        throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                "Missing required permission: payroll.read (or self / team access)");
    }

    @Transactional(readOnly = true)
    public PageResponse<PayslipDto> filterByStatus(PayslipStatus status, Pageable pageable) {
        return PageResponse.from(repo.findAllByStatus(status, pageable).map(PayslipDtoMapper::of));
    }

    @Transactional(readOnly = true)
    public PdfUrlResponse pdfUrl(long id) {
        Payslip p = load(id);
        if (accessFor(p.getEmployeeId()) == Access.RESTRICTED && p.getStatus() == PayslipStatus.DRAFT) {
            throw new NotFoundError(PayrollErrorCode.PAYSLIP_NOT_FOUND, "payslip not found");
        }
        if (p.getPdfObjectKey() == null) {
            throw new NotFoundError(PayrollErrorCode.PAYSLIP_NOT_FOUND, "PDF not yet generated");
        }
        return new PdfUrlResponse(storage.signedGetUrl(props.payslips().bucket(),
                p.getPdfObjectKey(), props.payslips().downloadUrlTtl()));
    }

    @Transactional
    public PayslipDto hold(long id, HoldPayslipRequest req) {
        Payslip p = load(id);
        // Hold is a HR review tool: applies pre-post (DRAFT → HELD, so the
        // post step skips it) and post-post (PAID → HELD, after which the
        // operator can release in a later run). RELEASE_PENDING / RELEASED
        // are terminal in this lifecycle and reject.
        if (p.getStatus() != PayslipStatus.DRAFT && p.getStatus() != PayslipStatus.PAID) {
            throw new ConflictError(PayrollErrorCode.PAYSLIP_INVALID_STATE,
                    "hold requires DRAFT or PAID (current: " + p.getStatus() + ")");
        }
        p.setStatus(PayslipStatus.HELD);
        p.setHeldReason(req.reason());
        outbox.publish(PayrollEvents.TOPIC, "payslip:" + id, PayrollEvents.SALARY_HELD,
                new PayrollEvents.SalaryHeld(id, p.getEmployeeId(), p.getPayrollRunId(),
                        req.reason(), CurrentUserHolder.usernameOrSystem()));
        audit.emit("payslip", String.valueOf(id), "salary_held", null,
                Map.of("reason", req.reason()), true);
        return get(id);
    }

    @Transactional
    public PayslipDto unhold(long id, HoldPayslipRequest req) {
        Payslip p = load(id);
        if (p.getStatus() != PayslipStatus.HELD) {
            throw new ConflictError(PayrollErrorCode.PAYSLIP_INVALID_STATE,
                    "unhold requires HELD");
        }
        PayrollRun r = run(p.getPayrollRunId());
        if (r.getStatus() == PayrollRunStatus.POSTED) {
            throw new ConflictError(PayrollErrorCode.PAYSLIP_INVALID_STATE,
                    "run is POSTED — use release instead of unhold");
        }
        // Pre-post unhold goes back to DRAFT (review state) so the run's
        // post step can promote it to PAID alongside everyone else.
        p.setStatus(PayslipStatus.DRAFT);
        p.setHeldReason(null);
        outbox.publish(PayrollEvents.TOPIC, "payslip:" + id, PayrollEvents.SALARY_UNHELD,
                new PayrollEvents.SalaryUnheld(id, p.getEmployeeId(), p.getPayrollRunId(),
                        req.reason(), CurrentUserHolder.usernameOrSystem()));
        audit.emit("payslip", String.valueOf(id), "salary_unheld", null,
                Map.of("reason", req.reason()), true);
        return get(id);
    }

    @Transactional
    public PayslipDto release(long id, HoldPayslipRequest req) {
        Payslip p = load(id);
        if (p.getStatus() != PayslipStatus.HELD) {
            throw new ConflictError(PayrollErrorCode.PAYSLIP_INVALID_STATE,
                    "release requires HELD");
        }
        PayrollRun r = run(p.getPayrollRunId());
        if (r.getStatus() != PayrollRunStatus.POSTED) {
            throw new ConflictError(PayrollErrorCode.PAYSLIP_INVALID_STATE,
                    "release requires the original run to be POSTED");
        }
        p.setStatus(PayslipStatus.RELEASE_PENDING);
        outbox.publish(PayrollEvents.TOPIC, "payslip:" + id, PayrollEvents.SALARY_RELEASE_QUEUED,
                new PayrollEvents.SalaryReleaseQueued(id, p.getEmployeeId(),
                        p.getPayrollRunId(), p.getNet(), req.reason(),
                        CurrentUserHolder.usernameOrSystem()));
        audit.emit("payslip", String.valueOf(id), "salary_release_queued", null,
                Map.of("reason", req.reason(), "amount", p.getNet()), true);
        return get(id);
    }

    private Payslip load(long id) {
        return repo.findById(id).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.PAYSLIP_NOT_FOUND, "payslip not found"));
    }

    private PayrollRun run(long runId) {
        return runRepo.findById(runId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND, "payroll run not found"));
    }
}
