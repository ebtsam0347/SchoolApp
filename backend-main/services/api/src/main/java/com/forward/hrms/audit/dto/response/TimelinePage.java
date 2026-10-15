package com.forward.hrms.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Cursor-paginated timeline response. {@code nextCursor} is null when the
 * list is exhausted; otherwise pass it back as {@code cursor=...} on the
 * next request.
 */
@Schema(description = "Cursor-paginated timeline response.")
public record TimelinePage(
        List<TimelineEntry> content,

        @Schema(description = "Opaque cursor for the next page, null at end.",
                example = "MjAyNi0wNC0yOFQwOToxMjozM1o6NTQyMQ", nullable = true)
        String nextCursor
) {}
