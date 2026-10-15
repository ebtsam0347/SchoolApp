package com.forward.hrms.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Per-topic consumer state — partition end offsets, committed offsets, and lag.")
public record HealthResponse(
        @Schema(description = "Per-topic snapshot.")
        List<TopicHealth> topics
) {

    @Schema(description = "Per-topic consumer state.")
    public record TopicHealth(
            @Schema(example = "employee.audit") String topic,
            @Schema(example = "audit.employee") String consumerGroup,
            @Schema(description = "Sum of (endOffset - committedOffset) across partitions.", example = "0")
            long totalLag,
            List<PartitionHealth> partitions
    ) {}

    @Schema(description = "Per-partition consumer state.")
    public record PartitionHealth(
            @Schema(example = "0")     int partition,
            @Schema(example = "13042") long endOffset,
            @Schema(example = "13042") long committedOffset,
            @Schema(example = "0")     long lag
    ) {}
}
