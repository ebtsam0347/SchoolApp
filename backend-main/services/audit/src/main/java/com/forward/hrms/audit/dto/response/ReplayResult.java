package com.forward.hrms.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Outcome of a replay request.")
public record ReplayResult(

        @Schema(description = "Number of records inserted (or, in dry-run, that would have been inserted; "
                + "in count-only mode, the approximate upper-bound row count from the offset range).",
                example = "412")
        int replayed,

        @Schema(description = "Number of records skipped — duplicates (already ingested) and malformed payloads.",
                example = "53")
        int skipped,

        @Schema(description = "Number of records that failed with an exception during ingest.", example = "0")
        int errors,

        @Schema(description = "Echoes the request — true if no rows were written.", example = "false")
        boolean dryRun,

        @Schema(description = "Set to true on count-only responses (cheap upper-bound estimate, no Kafka poll).",
                example = "false", nullable = true)
        Boolean countOnly,

        @Schema(description = "Short-lived token issued on dryRun:true responses. Echo back as `replayToken` "
                + "on the non-dry call. Null on non-dry-run responses.",
                example = "01HZK7G6Y0PSV2TPC9N9XSH3WC", nullable = true)
        String replayToken
) {}
