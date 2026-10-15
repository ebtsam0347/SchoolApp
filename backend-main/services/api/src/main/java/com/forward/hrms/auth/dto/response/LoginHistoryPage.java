package com.forward.hrms.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Cursor-paginated login history. Same shape as audit-service's
 * {@code CursorPage<AuditEventDto>} — auth proxies through, mapping
 * to {@link LoginHistoryEntry} on the way out.
 */
@Schema(description = "Cursor-paginated login history.")
public record LoginHistoryPage(
        List<LoginHistoryEntry> content,
        @Schema(description = "Opaque cursor for the next page, null at end.", nullable = true)
        String nextCursor
) {}
