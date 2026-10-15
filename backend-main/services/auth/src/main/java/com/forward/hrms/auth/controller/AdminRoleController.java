package com.forward.hrms.auth.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.forward.hrms.auth.dto.request.CreateRoleRequest;
import com.forward.hrms.auth.dto.request.UpdateRoleRequest;
import com.forward.hrms.auth.dto.response.PermissionDto;
import com.forward.hrms.auth.dto.response.RoleDto;
import com.forward.hrms.auth.service.AdminRoleService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin — Roles & Permissions",
        description = "Catalog reads, role write APIs, and the reverse permission→roles lookup. "
                + "Permissions remain seed-only; roles support custom additions (HR ask: "
                + "\"Payroll Read-only\" for an audit consultant). "
                + "Requires `auth.roles.read` / `auth.roles.write` permissions.")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService service;

    @Operation(summary = "List roles with attached permissions",
            description = "Service-account roles (POLLER, ATTENDANCE_SVC) are excluded by default — "
                    + "they back machine identities and shouldn't show up in the user-assignment "
                    + "dropdown. Pass `includeServiceAccounts=true` from the role catalog admin "
                    + "screen when the full set is wanted. Requires `auth.roles.read`.")
    @GetMapping("/roles")
    @RequirePermission("auth.roles.read")
    public List<RoleDto> listRoles(
            @Parameter(description = "Include service-account roles in the response.")
            @RequestParam(name = "includeServiceAccounts", defaultValue = "false") boolean includeServiceAccounts) {
        return service.listRoles(includeServiceAccounts);
    }

    @Operation(summary = "List permissions",
            description = "Use the codes as the value for `@RequirePermission` and frontend gating. Requires `auth.roles.read`.")
    @GetMapping("/permissions")
    @RequirePermission("auth.roles.read")
    public List<PermissionDto> listPermissions() {
        return service.listPermissions();
    }

    @Operation(summary = "Create a custom role",
            description = "Code is immutable once created. Use UPPER_SNAKE_CASE codes (no ROLE_ prefix). "
                    + "All permissions in the request must already exist in /admin/permissions. Requires `auth.roles.write`.")
    @ApiResponse(responseCode = "201", description = "Role created.")
    @ApiResponse(responseCode = "404", description = "One of the requested permission codes is unknown.")
    @ApiResponse(responseCode = "409", description = "Role code already in use.")
    @PostMapping("/roles")
    @RequirePermission("auth.roles.write")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDto createRole(@Valid @RequestBody CreateRoleRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a role",
            description = "Null fields are left unchanged. To replace permissions, send the full target set in "
                    + "`permissions` (not a diff). Both system-seeded and custom roles can be patched here; "
                    + "the `code` field is immutable. Requires `auth.roles.write`.")
    @ApiResponse(responseCode = "200", description = "Role updated.")
    @ApiResponse(responseCode = "404", description = "Role not found, or one of the requested permission codes is unknown.")
    @PatchMapping("/roles/{id}")
    @RequirePermission("auth.roles.write")
    public RoleDto updateRole(@Parameter(description = "Role id") @PathVariable long id,
                              @Valid @RequestBody UpdateRoleRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Delete a custom role",
            description = "Refuses if the role is system-seeded (EMPLOYEE | MANAGER | HR | ADMIN) or if any "
                    + "user currently holds it. Detach users first, then retry. Requires `auth.roles.write`.")
    @ApiResponse(responseCode = "204", description = "Role deleted.")
    @ApiResponse(responseCode = "404", description = "Role not found.")
    @ApiResponse(responseCode = "409", description = "Role is system-seeded or held by one or more users.")
    @DeleteMapping("/roles/{id}")
    @RequirePermission("auth.roles.write")
    public ResponseEntity<Void> deleteRole(@Parameter(description = "Role id") @PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Roles that include a permission",
            description = "Reverse lookup — given a permission code, return the roles that grant it. "
                    + "Lets the roles page render \"who has this?\" without pulling the whole role catalog "
                    + "and joining client-side. Requires `auth.roles.read`.")
    @ApiResponse(responseCode = "200", description = "Roles ordered by code, possibly empty.")
    @ApiResponse(responseCode = "404", description = "No permission with that code.")
    @GetMapping("/permissions/{code}/roles")
    @RequirePermission("auth.roles.read")
    public List<RoleDto> rolesByPermission(
            @Parameter(description = "Permission code (e.g. `auth.users.write`).", example = "auth.users.write")
            @PathVariable String code) {
        return service.rolesByPermission(code);
    }
}
