package com.forward.hrms.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forward.hrms.auth.dto.request.LoginRequest;
import com.forward.hrms.auth.dto.request.RefreshRequest;
import com.forward.hrms.auth.dto.response.TokenResponse;
import com.forward.hrms.auth.service.LoginService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Authentication", description = "Login, refresh, logout. These three endpoints are unauthenticated.")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@SecurityRequirements // these endpoints don't need bearer auth
public class AuthController {

    private final LoginService loginService;

    @Operation(
            summary = "Log in with username + password",
            description = """
                    Returns an access + refresh token pair plus session id.
                    Failed attempts increment a counter and lock the account temporarily.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens issued."),
            @ApiResponse(responseCode = "400", description = "Validation failed (blank credentials)."),
            @ApiResponse(responseCode = "401", description = "Bad credentials or account disabled."),
            @ApiResponse(responseCode = "423", description = "Account temporarily locked after too many failures.")
    })
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return loginService.login(req);
    }

    @Operation(
            summary = "Mint a new access + refresh pair",
            description = "Rotates the refresh token. The previous refresh token is revoked atomically.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued."),
            @ApiResponse(responseCode = "401", description = "Refresh token unknown, expired, or already revoked.")
    })
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return loginService.refresh(req.refreshToken());
    }

    @Operation(
            summary = "End the current session",
            description = "Revokes the refresh token and the backing session row. Idempotent.")
    @ApiResponse(responseCode = "204", description = "Session revoked (or already revoked).")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        String by = CurrentUserHolder.get()
                .map(CurrentUser::username)
                .orElse(CurrentUser.SYSTEM_USERNAME);
        loginService.logout(req.refreshToken(), by);
        return ResponseEntity.noContent().build();
    }
}
