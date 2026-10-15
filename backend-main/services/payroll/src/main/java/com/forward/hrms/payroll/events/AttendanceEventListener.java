package com.forward.hrms.payroll.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.payroll.service.PayrollRunService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Consumes attendance-service events. Today only {@code
 * attendance.period_locked} matters: when the operator locks a month from
 * the attendance side, payroll freezes the matching run — see
 * {@link PayrollRunService#onAttendancePeriodLocked}. Idempotent: the
 * handler no-ops on re-delivery and on the payroll-initiated lock path
 * (which sets LOCKED itself), so the two directions converge.
 *
 * <p>Topic / event-type strings are inlined rather than pulled from a
 * shared module: payroll is the only attendance-events consumer, so per
 * the "promote to commons after the third caller" rule there is no
 * {@code commons-attendance-events} yet. They mirror attendance's
 * {@code AttendanceEvents.TOPIC} / {@code AttendanceEvents.PERIOD_LOCKED}
 * contract — keep them in sync if those rename.
 */
@Slf4j
@Component
public class AttendanceEventListener {

    /** Equals {@code KafkaTopics.events("attendance")}; literal so it is usable in the annotation. */
    private static final String ATTENDANCE_TOPIC = "attendance.events";
    private static final String PERIOD_LOCKED = "attendance.period_locked";

    private final PayrollRunService runs;
    private final ObjectMapper json;

    public AttendanceEventListener(PayrollRunService runs,
                                   @Qualifier("outboxObjectMapper") ObjectMapper json) {
        this.runs = runs;
        this.json = json;
    }

    @KafkaListener(topics = ATTENDANCE_TOPIC, groupId = "payroll.attendance-events")
    @Transactional
    public void onAttendanceEvent(String message) throws IOException {
        JsonNode root = json.readTree(message);
        if (!PERIOD_LOCKED.equals(root.path("eventType").asText(null))) return;
        JsonNode p = root.path("payload");
        int year = p.path("periodYear").asInt();
        int month = p.path("periodMonth").asInt();
        if (year <= 0 || month < 1 || month > 12) {
            log.warn("Ignoring attendance.period_locked with bad period {}-{}", year, month);
            return;
        }
        runs.onAttendancePeriodLocked(year, month);
    }
}
