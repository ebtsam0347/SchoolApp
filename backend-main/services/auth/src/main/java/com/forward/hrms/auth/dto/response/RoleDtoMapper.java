package com.forward.hrms.auth.dto.response;

import com.forward.hrms.auth.entity.Role;

import java.util.List;

public final class RoleDtoMapper {

    private RoleDtoMapper() {}

    public static RoleDto of(Role r) {
        List<String> perms = r.getPermissions().stream().map(p -> p.getCode()).sorted().toList();
        return new RoleDto(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.isSystem(), r.isServiceAccount(), perms);
    }
}
