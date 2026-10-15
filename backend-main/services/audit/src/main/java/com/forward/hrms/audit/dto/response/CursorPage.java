package com.forward.hrms.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Generic cursor-paginated response. Same shape as {@link TimelinePage}
 * but typed for any payload — used by /audit/by-entity and
 * /timeline/self/sign-in-events. {@code nextCursor} is null when the
 * list is exhausted; otherwise pass it back as {@code cursor=...} on
 * the next request.
 */
@Schema(description = "Cursor-paginated response.")
public record CursorPage<T>(
        List<T> content,

        @Schema(description = "Opaque cursor for the next page, null at end.",
                example = "MjAyNi0wNC0yOFQwOToxMjozM1o6NTQyMQ", nullable = true)
        String nextCursor
) {}
