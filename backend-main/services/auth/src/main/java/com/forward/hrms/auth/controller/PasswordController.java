package com.forward.hrms.auth.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forward.hrms.auth.dto.request.PasswordChangeRequest;
import com.forward.hrms.auth.dto.response.TokenResponse;
import com.forward.hrms.auth.service.PasswordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Password (self-service)",
        description = "Authenticated user changes their own password. Admin reset is under /admin/users.")
@RestController
@RequestMapping("/auth/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService service;

    @Operation(
            summary = "Change my password",
            description = "Validates the current password, applies the password policy, re-hashes "
                    + "(bcrypt cost 12), clears the must-change-password flag, rotates the caller's "
                    + "session refresh token, and returns a fresh access + refresh token pair so the "
                    + "FE can replace its cached tokens in one round-trip — the previous 204 left the "
                    + "old `pwChangeRequired: true` JWT in play until the next refresh.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed; new tokens issued."),
            @ApiResponse(responseCode = "400", description = "Old password wrong or new password fails policy."),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.")
    })
    @PostMapping("/change")
    public TokenResponse change(@Valid @RequestBody PasswordChangeRequest req) {
        return service.changeOwnPassword(req);
    }
}
