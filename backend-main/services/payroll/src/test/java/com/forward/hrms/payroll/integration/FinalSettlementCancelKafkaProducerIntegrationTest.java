package com.forward.hrms.payroll.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.kafka.outbox.OutboxPublisher;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.commons.testsupport.kafka.KafkaTestConsumer;
import com.forward.hrms.commons.testsupport.outbox.OutboxTestUtils;
import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.client.FairPriceShopClient;
import com.forward.hrms.payroll.client.dto.attendace.AttendanceSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.DurationDto;
import com.forward.hrms.payroll.client.dto.attendace.NormalOvertimeSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.TerminationAttendanceSummaryDto;
import com.forward.hrms.payroll.client.dto.employee.EmployeeTerminationInfo;
import com.forward.hrms.payroll.dto.request.ApprovalRequest;
import com.forward.hrms.payroll.dto.request.CancelSettlementRequest;
import com.forward.hrms.payroll.dto.request.CreateFinalSettlementRequest;
import com.forward.hrms.payroll.dto.response.FinalSettlementDto;
import com.forward.hrms.payroll.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.PayrollConfigRepository;
import com.forward.hrms.payroll.service.settlements.SettlementFinalizationService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Proves the REAL producer half of the payroll -> outbox -> Kafka chain
 * for CANCEL (and REJECT): calls the actual {@link SettlementFinalizationService}
 * methods (real outbox row written inside the real transaction), forces
 * the real {@link OutboxPublisher} to drain it (bypassing only the
 * {@code @Scheduled} timer — {@code OutboxPublisher.poll()} itself runs
 * for real, same as production), and reads the message back from the
 * real shared Kafka Testcontainer via {@link KafkaTestConsumer} — a raw
 * consumer with no relationship to payroll's own code, so this cannot
 * pass by accident from in-process state.
 *
 * <p>The consumer half of this chain (employee-service's real
 * {@code @KafkaListener} picking up a message from this same topic and
 * driving ExitService/TerminationSaga) is proved separately in
 * {@code PayrollEventKafkaConsumerIntegrationTest} (employee-service test
 * suite) — a single JUnit test can only boot one service's Spring
 * context, so the two genuinely-real halves are necessarily two test
 * classes, not one. Both use the exact same shared Kafka broker
 * ({@code SharedContainers.KAFKA}, JVM-singleton, reused across modules).
 */
@SpringBootTest
@WithTestcontainers
class FinalSettlementCancelKafkaProducerIntegrationTest {

    @Autowired SettlementFinalizationService settlements;
    @Autowired EmployeeFinalSettlementRepository settlementRepo;
    @Autowired EmployeeSalaryRepository salaryRepo;
    @Autowired EmployeePayrollSettingsRepository settingsRepo;
    @Autowired PayrollConfigRepository configRepo;
    @Autowired OutboxPublisher outboxPublisher;

    @MockitoBean EmployeeClient employeeClient;
    @MockitoBean AttendanceClient attendanceClient;
    @MockitoBean FairPriceShopClient fairPriceShopClient;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private static final AtomicLong NEXT_EMPLOYEE_ID = new AtomicLong(9_500_000L);

    @BeforeEach
    void cleanAndSeed() {
        settlementRepo.deleteAll();
        salaryRepo.deleteAll();
        settingsRepo.deleteAll();
        configRepo.deleteAll();

        PayrollConfig cfg = new PayrollConfig();
        cfg.setEffectiveFrom(LocalDate.now().minusYears(1));
        cfg.setEobiAmount(BigDecimal.ZERO);
        cfg.setPessiRatePercent(BigDecimal.ZERO);
        cfg.setPessiSalaryCap(BigDecimal.ZERO);
        cfg.setCanteenPerDayRateManagement(BigDecimal.ZERO);
        cfg.setCanteenPerDayRateOther(BigDecimal.ZERO);
        configRepo.save(cfg);

        CurrentUserHolder.set(new CurrentUser(4343L, "hr-tester", Set.of(),
                Set.of("payroll.read", "payroll.write")));
    }

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    @Test
    void cancel_reallyPublishesToKafka_readableByAnIndependentConsumer() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));
        FinalSettlementDto created = create(employeeId);

        try (KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, PayrollEvents.TOPIC)) {

            settlements.cancel(created.id(), new CancelSettlementRequest("HR mistake"));

            var cancelled = settlementRepo.findById(created.id()).orElseThrow();
            assertThat(cancelled.getStatus()).isEqualTo(EmployeeFinalSettlementStatus.CANCELLED);

            // Force the real OutboxPublisher to drain now instead of waiting
            // on its @Scheduled timer — the publish call itself (Outbox row
            // -> KafkaTemplate.send -> real broker) is exactly the
            // production path, just triggered deterministically.
            OutboxTestUtils.flush(outboxPublisher);

            JsonNode match = findRecord(consumer, PayrollEvents.FINAL_SETTLEMENT_CANCELLED, created.id());
            assertThat(match)
                    .as("a real Kafka record for this settlement's cancellation must be readable "
                            + "by an independent consumer with no code relationship to payroll")
                    .isNotNull();
            assertThat(match.path("payload").path("employeeId").asLong()).isEqualTo(employeeId);
        }
    }

    @Test
    void reject_reallyPublishesToKafka_readableByAnIndependentConsumer() {
        long employeeId = seedTerminatingEmployee(new BigDecimal("50000.00"));
        FinalSettlementDto created = create(employeeId);

        try (KafkaTestConsumer consumer = new KafkaTestConsumer(bootstrapServers, PayrollEvents.TOPIC)) {

            settlements.reject(created.id(), new ApprovalRequest("wrong employee"));

            var rejected = settlementRepo.findById(created.id()).orElseThrow();
            assertThat(rejected.getStatus()).isEqualTo(EmployeeFinalSettlementStatus.REJECTED);

            OutboxTestUtils.flush(outboxPublisher);

            JsonNode match = findRecord(consumer, PayrollEvents.FINAL_SETTLEMENT_REJECTED, created.id());
            assertThat(match).isNotNull();
            assertThat(match.path("payload").path("employeeId").asLong()).isEqualTo(employeeId);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * The shared Kafka broker is a JVM/session-reused Testcontainer (see
     * SharedContainers — {@code withReuse(true)}), so payroll.events
     * already carries a large backlog from earlier test runs by the time
     * this consumer subscribes from the EARLIEST offset. A plain
     * {@code drain(1, timeout)} can satisfy its count from stale backlog
     * before ever reaching the message this test just published, so page
     * through in growing batches, parsing each record as real JSON
     * (not a brittle raw-string contains check — key ordering/spacing in
     * the wire format isn't part of the contract) until the specific
     * settlementId + eventType this test cares about shows up.
     */
    private JsonNode findRecord(KafkaTestConsumer consumer, String eventType, long settlementId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        int batch = 100;
        while (System.nanoTime() < deadline) {
            List<ConsumerRecord<String, String>> records = consumer.drain(batch, Duration.ofSeconds(3));
            for (ConsumerRecord<String, String> r : records) {
                JsonNode envelope = parseOrNull(r.value());
                if (envelope == null) continue;
                if (!eventType.equals(envelope.path("eventType").asText(null))) continue;
                if (envelope.path("payload").path("settlementId").asLong(-1) == settlementId) {
                    return envelope;
                }
            }
            batch += 100;
        }
        return null;
    }

    private JsonNode parseOrNull(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private FinalSettlementDto create(long employeeId) {
        return settlements.create(new CreateFinalSettlementRequest(
                employeeId,
                LocalDate.now(),
                SettlementAdjustmentType.NONE,
                NoticePayPercentage.NONE,
                ForcedTerminationDeductionPercentage.PERCENT_0,
                null,
                "termination settlement"
        ));
    }

    private long seedTerminatingEmployee(BigDecimal basic) {
        long employeeId = NEXT_EMPLOYEE_ID.getAndIncrement();

        EmployeePayrollSettings settings = new EmployeePayrollSettings();
        settings.setEmployeeId(employeeId);
        settings.setHireDate(LocalDate.now().minusMonths(3));
        settings.setEobiEnrolled(false);
        settingsRepo.save(settings);

        EmployeeSalary salary = new EmployeeSalary();
        salary.setEmployeeId(employeeId);
        salary.setBasic(basic);
        salary.setEffectiveFrom(LocalDate.now().minusMonths(2));
        salaryRepo.save(salary);

        when(employeeClient.terminationInfo(employeeId)).thenReturn(new EmployeeTerminationInfo(
                employeeId, "EMP-" + employeeId, "Test Employee " + employeeId,
                "Dept", "Section", "Designation", "G1", GradeType.LABOUR, LocalDate.now().minusMonths(3)));

        LocalDate lwd = LocalDate.now();
        LocalDate periodStart = lwd.withDayOfMonth(1);
        AttendanceSummaryDto attendance = new AttendanceSummaryDto(
                lwd.getDayOfMonth(), 0, 0, 0, 0, 0, 0, 0);
        DurationDto zeroDuration = new DurationDto(0, "0h 0m");
        NormalOvertimeSummaryDto overtime = new NormalOvertimeSummaryDto(
                zeroDuration, zeroDuration, zeroDuration, zeroDuration);
        when(attendanceClient.terminationAttendanceSummary(employeeId, periodStart, lwd))
                .thenReturn(new TerminationAttendanceSummaryDto(employeeId, periodStart, lwd, attendance, overtime));

        when(attendanceClient.terminationLeavesSummary(employeeId, lwd))
                .thenReturn(new TerminationLeavesSummaryDto(null, null, BigDecimal.ZERO, BigDecimal.ZERO));

        return employeeId;
    }
}
