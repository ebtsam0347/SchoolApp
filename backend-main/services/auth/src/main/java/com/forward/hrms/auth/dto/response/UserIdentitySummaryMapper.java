package com.forward.hrms.auth.dto.response;

import com.forward.hrms.auth.entity.User;

public final class UserIdentitySummaryMapper {

    private UserIdentitySummaryMapper() {}

    public static UserIdentitySummary of(User u) {
        return new UserIdentitySummary(u.getId(), u.getUsername(), u.getFullName(), u.getStatus());
    }
}
