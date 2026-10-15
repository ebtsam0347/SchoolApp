package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.dto.response.AccountSummary;
import com.forward.hrms.bff.dto.response.PersonaHints;
import com.forward.hrms.bff.service.AccountSummaryService;
import com.forward.hrms.bff.service.PersonaHintsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account (self-service)",
        description = "Composite views for the signed-in user's /account screens. "
                + "Restricted to the caller's own data — admin views live under /bff/admin.")
@RestController
@RequestMapping("/bff/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountSummaryService service;
    private final PersonaHintsService personaHints;

    @Operation(summary = "Aggregated /account payload",
            description = "Returns identity + active sessions (partitioned into current/others) "
                    + "+ top user-visible timeline entries in a single round-trip. Lets the "
                    + "/account layout paint its user-stripe and overview tab without a fan-out "
                    + "from the browser.")
    @ApiResponse(responseCode = "200", description = "Composite payload assembled from auth + audit.")
    @ApiResponse(responseCode = "401", description = "Missing identity headers (gateway didn't authenticate).")
    @ApiResponse(responseCode = "502", description = "A required downstream (auth or audit) was unreachable.")
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public AccountSummary summary() {
        return service.summary();
    }

    @Operation(summary = "Persona hints for the signed-in caller",
            description = "Lightweight flags the FE persona resolver needs on top of /auth/me — "
                    + "today just the manager signal (does the caller have direct reports). "
                    + "Soft-fails to all-false rather than 4xx-ing when the caller has no ESS-"
                    + "linked employee record so sign-in stays unblocked.")
    @ApiResponse(responseCode = "200", description = "Persona hints (worst case: all-false).")
    @ApiResponse(responseCode = "401", description = "Missing identity headers.")
    @GetMapping("/persona-hints")
    @PreAuthorize("isAuthenticated()")
    public PersonaHints personaHints() {
        return personaHints.hints();
    }
}
