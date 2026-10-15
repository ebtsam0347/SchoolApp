package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.ApplyBonusInstallmentRequest;
import com.forward.hrms.payroll.dto.request.CreateBonusEventRequest;
import com.forward.hrms.payroll.dto.request.CreateBonusInstallmentRequest;
import com.forward.hrms.payroll.dto.request.UpdateBonusEventRequest;
import com.forward.hrms.payroll.dto.request.UpdateBonusInstallmentRequest;
import com.forward.hrms.payroll.dto.response.BonusEventDto;
import com.forward.hrms.payroll.dto.response.BonusInstallmentDto;
import com.forward.hrms.payroll.enums.BonusEventStatus;
import com.forward.hrms.payroll.service.BonusEventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Bonus events",
        description = "Eid bonus planning + installment apply. Requires `payroll.read` / `payroll.admin`.")
@RestController
@RequestMapping("/bonuses/events")
@RequiredArgsConstructor
public class BonusEventController {

    private final BonusEventService service;

    @Operation(summary = "List bonus events", description = "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public List<BonusEventDto> list(@Parameter @RequestParam(required = false, name = "fiscalYear") String fy,
                                    @Parameter @RequestParam(required = false) BonusEventStatus status) {
        return service.list(fy, status);
    }

    @Operation(summary = "Get one (with installments + per-employee amounts)",
            description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public BonusEventDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "Create a DRAFT event", description = "Requires `payroll.admin`.")
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public BonusEventDto create(@Valid @RequestBody CreateBonusEventRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a DRAFT event", description = "Requires `payroll.admin`.")
    @PatchMapping("/{id}")
    @RequirePermission("payroll.admin")
    public BonusEventDto update(@PathVariable long id,
                                @Valid @RequestBody UpdateBonusEventRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Delete a DRAFT event", description = "Requires `payroll.admin`.")
    @DeleteMapping("/{id}")
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @Operation(summary = "Activate (DRAFT → ACTIVE) — freezes per-employee amounts",
            description = "Requires `payroll.admin`.")
    @PostMapping("/{id}/activate")
    @RequirePermission("payroll.admin")
    public BonusEventDto activate(@PathVariable long id) {
        return service.activate(id);
    }

    @Operation(summary = "Close (ACTIVE → CLOSED) — no further installments",
            description = "Requires `payroll.admin`.")
    @PostMapping("/{id}/close")
    @RequirePermission("payroll.admin")
    public BonusEventDto close(@PathVariable long id) {
        return service.close(id);
    }

    // ----- installments -----

    @Operation(summary = "Add an installment to a bonus event",
            description = "Requires `payroll.admin`.")
    @PostMapping("/{eventId}/installments")
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public BonusInstallmentDto addInstallment(@PathVariable long eventId,
                                              @Valid @RequestBody CreateBonusInstallmentRequest req) {
        return service.addInstallment(eventId, req);
    }

    @Operation(summary = "Patch a DRAFT installment", description = "Requires `payroll.admin`.")
    @PatchMapping("/{eventId}/installments/{id}")
    @RequirePermission("payroll.admin")
    public BonusInstallmentDto updateInstallment(@PathVariable long eventId,
                                                 @PathVariable long id,
                                                 @Valid @RequestBody UpdateBonusInstallmentRequest req) {
        return service.updateInstallment(eventId, id, req);
    }

    @Operation(summary = "Delete a DRAFT installment", description = "Requires `payroll.admin`.")
    @DeleteMapping("/{eventId}/installments/{id}")
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInstallment(@PathVariable long eventId, @PathVariable long id) {
        service.deleteInstallment(eventId, id);
    }

    @Operation(summary = "Apply an installment (DRAFT → APPLIED). Idempotent.",
            description = "Walks bonus_event_amounts, writes one off_cycle_payments row per " +
                    "eligible employee with category=BONUS. Requires `payroll.admin`.")
    @PostMapping("/{eventId}/installments/{id}/apply")
    @RequirePermission("payroll.admin")
    public BonusInstallmentDto apply(@PathVariable long eventId, @PathVariable long id,
                                     @RequestBody(required = false) ApplyBonusInstallmentRequest req) {
        return service.applyInstallment(eventId, id,
                req == null ? new ApplyBonusInstallmentRequest(null) : req);
    }
}
