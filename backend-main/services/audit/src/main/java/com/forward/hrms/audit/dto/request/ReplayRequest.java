package com.forward.hrms.audit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Replay a bounded Kafka offset range from one audit topic. Idempotent ingest makes duplicates safe.")
public record ReplayRequest(

        @NotBlank
        @Schema(description = "Audit topic to replay. Must be one of the configured `<svc>.audit` topics.",
                example = "employee.audit", requiredMode = Schema.RequiredMode.REQUIRED)
        String topic,

        @PositiveOrZero
        @Schema(description = "Inclusive lower bound (offset). 0-based.",
                example = "12345", requiredMode = Schema.RequiredMode.REQUIRED)
        long fromOffset,

        @PositiveOrZero
        @Schema(description = "Exclusive upper bound (offset). Must be greater than `fromOffset`.",
                example = "13000", requiredMode = Schema.RequiredMode.REQUIRED)
        long toOffset,

        @Schema(description = "Restrict to a single partition. Omit to replay the offset range across all partitions.",
                example = "0", nullable = true)
        Integer partition,

        @Schema(description = "If true, count what would be replayed without writing. The response includes "
                + "a `replayToken` you must echo back on the non-dry call within the next ~5 minutes.",
                example = "false", defaultValue = "false")
        Boolean dryRun,

        @Schema(description = "If true, return an approximate row count without polling Kafka. Cheap — useful "
                + "for the \"About to replay ~412 records\" prompt before the real button click. Mutually "
                + "exclusive with `dryRun`.",
                example = "false", defaultValue = "false", nullable = true)
        Boolean countOnly,

        @Schema(description = "Token returned by a previous `dryRun: true` call. Required for `dryRun: false` "
                + "to prevent firing a non-dry replay without dry-running first. Tokens are valid for ~5 minutes.",
                example = "01HZK7G6Y0PSV2TPC9N9XSH3WC", nullable = true)
        String replayToken
) {}
