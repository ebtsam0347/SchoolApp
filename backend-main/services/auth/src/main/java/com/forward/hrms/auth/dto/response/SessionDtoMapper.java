package com.forward.hrms.auth.dto.response;

import com.forward.hrms.auth.entity.Session;
import com.forward.hrms.auth.service.UserAgentParser;

public final class SessionDtoMapper {

    private SessionDtoMapper() {}

    public static SessionDto of(Session s, long currentSessionId) {
        UserAgentParser.Parsed parsed = UserAgentParser.parse(s.getUserAgent());
        return new SessionDto(
                s.getId(),
                s.getDeviceLabel(),
                parsed.family().name(),
                toNameAndVersion(parsed.browser()),
                toNameAndVersion(parsed.os()),
                s.getIpAddress(),
                s.getUserAgent(),
                s.getIssuedAt(),
                s.getLastSeenAt(),
                s.getExpiresAt(),
                s.getId() == currentSessionId,
                s.getRevokedAt(),
                s.getRevokedBy());
    }

    private static SessionDto.NameAndVersion toNameAndVersion(UserAgentParser.NameAndVersion v) {
        return v == null ? null : new SessionDto.NameAndVersion(v.name(), v.version());
    }
}
