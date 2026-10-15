package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.context.RequestContext;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.payroll.dto.request.CreateFiscalYearRequest;
import com.forward.hrms.payroll.dto.request.UpdateFiscalYearRequest;
import com.forward.hrms.payroll.dto.response.FiscalYearDto;
import com.forward.hrms.payroll.entity.FiscalYear;
import com.forward.hrms.payroll.enums.FiscalYearStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.FiscalYearRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FiscalYearService {

    private final FiscalYearRepository repo;

    @Transactional(readOnly = true)
    public List<FiscalYearDto> list() {
        return repo.findAllByOrderByStartDateDesc().stream().map(FiscalYearDto::of).toList();
    }

    @Transactional(readOnly = true)
    public FiscalYearDto current() {
        return FiscalYearDto.of(currentEntity());
    }

    @Transactional(readOnly = true)
    public FiscalYear currentEntity() {
        return repo.findByStatus(FiscalYearStatus.CURRENT)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.FISCAL_YEAR_NOT_FOUND,
                        "no current fiscal year configured"));
    }

    @Transactional(readOnly = true)
    public FiscalYear forPeriod(int year, int month) {
        LocalDate firstOfMonth = LocalDate.of(year, month, 1);
        return repo.findCovering(firstOfMonth)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.FISCAL_YEAR_NOT_FOUND,
                        "no fiscal year covers " + firstOfMonth));
    }

    @Transactional
    public FiscalYearDto create(CreateFiscalYearRequest req) {
        if (repo.existsById(req.code())) {
            throw new ConflictError(PayrollErrorCode.FISCAL_YEAR_INVALID,
                    "fiscal year " + req.code() + " already exists");
        }
        if (!req.endDate().isAfter(req.startDate())) {
            throw new ConflictError(PayrollErrorCode.FISCAL_YEAR_INVALID,
                    "endDate must be after startDate");
        }
        FiscalYear fy = new FiscalYear();
        fy.setCode(req.code());
        fy.setStartDate(req.startDate());
        fy.setEndDate(req.endDate());
        fy.setStatus(coversToday(req.startDate(), req.endDate())
                ? FiscalYearStatus.CURRENT
                : FiscalYearStatus.FUTURE);
        LocalDateTime now = LocalDateTime.now();
        String actor = CurrentUserHolder.usernameOrSystem();
        fy.setCreatedAt(now);
        fy.setUpdatedAt(now);
        fy.setCreatedBy(actor);
        fy.setUpdatedBy(actor);
        fy.setIpAddress(RequestContext.current().map(RequestContext::ipAddress).orElse(null));

        // If we're inserting a CURRENT row, demote any other CURRENT row first.
        // Hibernate's action queue runs INSERTs before UPDATEs by default, so
        // the demotion UPDATE has to be flushed before save() — otherwise the
        // partial unique index `uq_fiscal_years_current` rejects the INSERT
        // with two CURRENT rows momentarily live. Same shape as
        // EmployeeSalaryService.reviseInternal:99.
        if (fy.getStatus() == FiscalYearStatus.CURRENT) {
            boolean demoted = repo.findByStatus(FiscalYearStatus.CURRENT).map(existing -> {
                existing.setStatus(FiscalYearStatus.CLOSED);
                touch(existing);
                return true;
            }).orElse(false);
            if (demoted) repo.flush();
        }
        return FiscalYearDto.of(repo.save(fy));
    }

    @Transactional
    public FiscalYearDto setStatus(String code, UpdateFiscalYearRequest req) {
        FiscalYear fy = repo.findById(code).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.FISCAL_YEAR_NOT_FOUND, "fiscal year not found"));
        if (req.status() == FiscalYearStatus.CURRENT) {
            repo.findByStatus(FiscalYearStatus.CURRENT)
                    .filter(other -> !other.getCode().equals(code))
                    .ifPresent(other -> {
                        other.setStatus(FiscalYearStatus.CLOSED);
                        touch(other);
                    });
        }
        fy.setStatus(req.status());
        touch(fy);
        return FiscalYearDto.of(fy);
    }

    private boolean coversToday(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now();
        return !today.isBefore(start) && !today.isAfter(end);
    }

    private void touch(FiscalYear fy) {
        fy.setUpdatedAt(LocalDateTime.now());
        fy.setUpdatedBy(CurrentUserHolder.usernameOrSystem());
    }
}
