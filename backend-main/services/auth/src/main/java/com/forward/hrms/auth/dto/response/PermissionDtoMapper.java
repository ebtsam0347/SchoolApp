package com.forward.hrms.auth.dto.response;

import com.forward.hrms.auth.entity.Permission;
import com.forward.hrms.auth.service.PermissionCatalog;

public final class PermissionDtoMapper {

    private PermissionDtoMapper() {}

    public static PermissionDto of(Permission p) {
        return new PermissionDto(
                p.getId(),
                p.getCode(),
                p.getName(),
                PermissionCatalog.groupOf(p.getCode()),
                PermissionCatalog.descriptionOf(p.getCode())
        );
    }
}
