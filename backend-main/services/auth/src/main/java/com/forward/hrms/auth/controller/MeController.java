package com.forward.hrms.auth.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forward.hrms.auth.dto.response.MeResponse;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.AuthErrorCode;
import com.forward.hrms.auth.repository.UserRepository;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Current user", description = "Identity, roles, and permissions for the authenticated user.")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository users;

    @Operation(
            summary = "Get the current user's profile and permissions",
            description = "Use this on app boot to drive role/permission-gated UI.")
    @ApiResponse(responseCode = "200", description = "Profile returned.")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.")
    @GetMapping("/me")
    @Transactional(readOnly = true)
    public MeResponse me() {
        CurrentUser cu = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("not authenticated"));
        User u = users.findById(cu.userId())
                .orElseThrow(() -> new NotFoundError(AuthErrorCode.USER_NOT_FOUND, "user not found"));
        List<String> roles = u.getRoles().stream().map(r -> r.getCode()).sorted().toList();
        List<String> perms = u.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return new MeResponse(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getEmail(),
                u.isEmployeeLinked(),
                u.isMustChangePw(),
                roles,
                perms,
                u.getLastLoginAt());
    }
}
