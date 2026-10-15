package com.forward.hrms.bff.dto.response.dashboard;

import com.forward.hrms.audit.dto.response.AuditEventDto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "AdminDashboard",
        description = "Composite for the Admin persona. System-health slice; feeds the Admin " +
                "dashboard hero.")
public record AdminDashboard(
        SystemHealth systemHealth,
        List<AuditDay> auditVolume7d,
        NeedsYou needsYou,
        List<AuthHourBucket> authEventsByHour,
        List<AuditEventDto> recentRoleChanges
) {
    @Schema(name = "AdminDashboard.SystemHealth")
    public record SystemHealth(
            String overall,
            SagaSummary sagas,
            ReplayQueue auditReplayQueue
    ) {}

    @Schema(name = "AdminDashboard.SagaSummary",
            description = "Saga health rollup. Counts default to zero until the audit / saga " +
                    "list endpoint ships (D-S4).")
    public record SagaSummary(int running, int failed24h, List<SagaHealth> list) {}

    @Schema(name = "AdminDashboard.SagaHealth")
    public record SagaHealth(String id, String name, String status, List<Integer> failureRate24h) {}

    @Schema(name = "AdminDashboard.ReplayQueue",
            description = "Audit consumer-lag summary. Pulled from /admin/audit/health when " +
                    "available; defaults to zero/null when the caller lacks audit.admin.")
    public record ReplayQueue(int depth, long oldestSeconds) {}

    @Schema(name = "AdminDashboard.AuditDay")
    public record AuditDay(LocalDate date, long count) {}

    @Schema(name = "AdminDashboard.NeedsYou")
    public record NeedsYou(
            QueueSection failedSagas,
            QueueSection permissionChanges24h,
            QueueSection lockedAccounts
    ) {}

    @Schema(name = "AdminDashboard.QueueSection")
    public record QueueSection(int count, List<QueueItem> items) {}

    @Schema(name = "AdminDashboard.QueueItem",
            description = "Generic admin queue row. `retryHref` is set on rows where re-arm is " +
                    "supported (failed sagas).")
    public record QueueItem(
            long id,
            String label,
            String summary,
            @Schema(nullable = true) String retryHref
    ) {}

    @Schema(name = "AdminDashboard.AuthHourBucket")
    public record AuthHourBucket(int hour, long logins, long failures) {}
}
