package com.forward.hrms.commons.core.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Boundary conversion between {@link LocalDateTime} (the project's
 * universal in-memory time type — JVM TZ=Asia/Karachi makes
 * wall-clock and instant trivially convertible) and {@link Instant}
 * (only needed at outer boundaries: the JWT library, JDBC TIMESTAMP
 * mapping, etc.).
 *
 * <p>Lives in {@code commons-core} so the {@code ZoneId.systemDefault()}
 * call is contained — the §5.2 doctrine ban on {@code ZoneId} /
 * {@code ZoneOffset} applies outside of {@code com.forward.hrms.commons..}
 * exactly so service code doesn't grow incidental TZ math. Boundary
 * conversions belong here.
 */
public final class InstantBridge {

    private InstantBridge() {}

    /** {@code Instant} → {@code LocalDateTime} in the JVM-default zone (Asia/Karachi). */
    public static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /** {@code LocalDateTime} → {@code Instant} in the JVM-default zone (Asia/Karachi). */
    public static Instant toInstant(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toInstant();
    }
}
