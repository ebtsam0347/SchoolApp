package com.forward.hrms.attendance.dto.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.attendance.entity.DeviceSyncTask;
import com.forward.hrms.attendance.enums.DeviceSyncTaskStatus;
import com.forward.hrms.attendance.enums.DeviceSyncTaskType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(name = "DeviceSyncTaskDto",
        description = "A claimed sync task as the poller sees it. Payload shape varies by taskType " +
                "(see V0008 SQL comment in the attendance service).")
public record DeviceSyncTaskDto(
        long id,
        long deviceId,
        long employeeId,
        DeviceSyncTaskType taskType,
        @Schema(description = "Task payload as a free-form object; keys vary by taskType.")
        Map<String, Object> payload,
        DeviceSyncTaskStatus status,
        int attempts,
        LocalDateTime scheduledFor,
        @Schema(nullable = true) LocalDateTime claimedAt,
        @Schema(nullable = true) String lastError
) {
    // payload is a plain Map (not a Jackson JsonNode): SB4 serializes HTTP
    // responses with Jackson 3, which can't tree-serialize the Jackson 2
    // node this service's ObjectMapper builds — it would emit bean junk and
    // drop the real keys. A Map round-trips through either Jackson.
    public static DeviceSyncTaskDto of(DeviceSyncTask t, ObjectMapper json) {
        try {
            Map<String, Object> payload = (t.getPayload() == null || t.getPayload().isBlank())
                    ? Map.of()
                    : json.readValue(t.getPayload(), new TypeReference<Map<String, Object>>() {});
            return new DeviceSyncTaskDto(
                    t.getId(), t.getDeviceId(), t.getEmployeeId(), t.getTaskType(),
                    payload, t.getStatus(), t.getAttempts(), t.getScheduledFor(),
                    t.getClaimedAt(), t.getLastError());
        } catch (Exception e) {
            throw new IllegalStateException("corrupt sync task payload for id " + t.getId(), e);
        }
    }
}
