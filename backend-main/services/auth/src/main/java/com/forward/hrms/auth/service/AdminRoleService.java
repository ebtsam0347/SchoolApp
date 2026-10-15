package com.forward.hrms.auth.service;

import com.forward.hrms.auth.dto.request.CreateRoleRequest;
import com.forward.hrms.auth.dto.request.UpdateRoleRequest;
import com.forward.hrms.auth.dto.response.PermissionDto;
import com.forward.hrms.auth.dto.response.PermissionDtoMapper;
import com.forward.hrms.auth.dto.response.RoleDto;
import com.forward.hrms.auth.dto.response.RoleDtoMapper;
import com.forward.hrms.auth.entity.Permission;
import com.forward.hrms.auth.entity.Role;
import com.forward.hrms.auth.enums.AuthErrorCode;
import com.forward.hrms.auth.events.AuthEvents;
import com.forward.hrms.auth.repository.PermissionRepository;
import com.forward.hrms.auth.repository.RoleRepository;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Role lifecycle for the admin /admin/roles screens. Seeded roles
 * (system=true) can be edited but not deleted; custom roles can be
 * deleted only when no user holds them.
 */
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<RoleDto> listRoles(boolean includeServiceAccounts) {
        List<Role> rows = includeServiceAccounts ? roles.findAll() : roles.findByServiceAccountFalse();
        return rows.stream().map(RoleDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> listPermissions() {
        return permissions.findAll().stream().map(PermissionDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDto> rolesByPermission(String code) {
        if (permissions.findByCode(code).isEmpty()) {
            throw new NotFoundError(AuthErrorCode.PERMISSION_NOT_FOUND, "permission not found: " + code);
        }
        return roles.findByPermissionCode(code).stream().map(RoleDtoMapper::of).toList();
    }

    @Transactional
    public RoleDto create(CreateRoleRequest req) {
        if (roles.existsByCode(req.code())) {
            throw new ConflictError(AuthErrorCode.ROLE_CODE_TAKEN, "role code already in use: " + req.code());
        }
        Role r = new Role();
        r.setCode(req.code());
        r.setName(req.name());
        r.setDescription(req.description());
        r.setSystem(false);
        r.setPermissions(resolvePermissions(req.permissions()));
        roles.save(r);

        outbox.publish(AuthEvents.TOPIC, "role:" + r.getId(), AuthEvents.ROLE_CREATED,
                new AuthEvents.RoleCreated(r.getId(), r.getCode(), r.getName(), currentActor()));
        audit.emit("role", String.valueOf(r.getId()), "created", null, snapshot(r), false);
        return RoleDtoMapper.of(r);
    }

    @Transactional
    public RoleDto update(long roleId, UpdateRoleRequest req) {
        Role r = loadOrThrow(roleId);
        Map<String, Object> before = snapshot(r);

        if (req.name() != null && !req.name().isBlank()) {
            r.setName(req.name());
        }
        if (req.description() != null) {
            r.setDescription(req.description());
        }
        if (req.permissions() != null) {
            r.setPermissions(resolvePermissions(req.permissions()));
        }
        audit.emit("role", String.valueOf(r.getId()), "updated", before, snapshot(r), false);
        return RoleDtoMapper.of(r);
    }

    @Transactional
    public void delete(long roleId) {
        Role r = loadOrThrow(roleId);
        if (r.isSystem()) {
            throw new ConflictError(AuthErrorCode.ROLE_SYSTEM_LOCKED,
                    "system-seeded role cannot be deleted: " + r.getCode());
        }
        if (roles.isAssignedToAnyUser(r.getId())) {
            throw new ConflictError(AuthErrorCode.ROLE_IN_USE,
                    "role is held by one or more users: " + r.getCode());
        }
        Map<String, Object> before = snapshot(r);
        roles.delete(r);
        audit.emit("role", String.valueOf(roleId), "deleted", before, null, false);
    }

    private Role loadOrThrow(long id) {
        return roles.findById(id)
                .orElseThrow(() -> new NotFoundError(AuthErrorCode.ROLE_NOT_FOUND, "role not found"));
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        Set<Permission> out = new HashSet<>();
        for (String code : codes) {
            Permission p = permissions.findByCode(code)
                    .orElseThrow(() -> new NotFoundError(AuthErrorCode.PERMISSION_NOT_FOUND,
                            "unknown permission: " + code));
            out.add(p);
        }
        return out;
    }

    private static String currentActor() {
        return CurrentUserHolder.usernameOrSystem();
    }

    private static Map<String, Object> snapshot(Role r) {
        List<String> perms = r.getPermissions().stream().map(Permission::getCode).sorted().toList();
        return Map.of(
                "code", r.getCode(),
                "name", r.getName(),
                "description", r.getDescription() == null ? "" : r.getDescription(),
                "system", r.isSystem(),
                "serviceAccount", r.isServiceAccount(),
                "permissions", perms);
    }
}
