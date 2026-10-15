package com.forward.hrms.auth.dto.response;

import com.forward.hrms.auth.entity.User;

public final class UserDtoMapper {

    private UserDtoMapper() {}

    public static UserDto of(User u) {
        var roles = u.getRoles().stream().map(r -> r.getCode()).sorted().toList();
        return new UserDto(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getEmail(),
                u.getStatus(),
                u.isMustChangePw(),
                u.isEmployeeLinked(),
                roles,
                u.getLastLoginAt(),
                u.getLockedUntil(),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }
}
