package com.forward.hrms.payroll.integration;

import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.payroll.entity.ReportJob;
import com.forward.hrms.payroll.enums.ReportJobStatus;
import com.forward.hrms.payroll.repository.ReportJobRepository;
import com.forward.hrms.payroll.service.ReportJobHandler;
import com.forward.hrms.payroll.service.ReportJobRunner;
import com.forward.hrms.payroll.service.ReportJobService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Lifecycle of a {@code report_jobs} row, end-to-end:
 * QUEUED → (poll) → RUNNING → (handler) → READY + signed URL.
 * Plus the FAILED branch when the handler throws.
 *
 * <p>Catches Jasper + MinIO presign regressions in one shot. Storage is
 * mocked to keep the test focused on the runner's state-machine.
 */
@SpringBootTest
@WithTestcontainers
class ReportJobLifecycleIntegrationTest {

    private static final String TEST_TYPE = "TEST_REPORT";

    @Autowired ReportJobService reportJobService;
    @Autowired ReportJobRunner runner;
    @Autowired ReportJobRepository jobs;

    @MockitoBean ObjectStorage storage;

    /**
     * One handler is registered per type at context-init time. We register
     * a test handler that the test can flip between success / failure
     * paths via a static toggle.
     */
    @TestConfiguration
    static class TestHandlerConfig {
        @Bean
        @Primary
        ReportJobHandler testHandler() {
            return new TestableHandler();
        }
    }

    static class TestableHandler implements ReportJobHandler {
        static volatile boolean throwOnNext = false;
        static volatile String stubbedKey = "reports/payroll/test/output.pdf";
        static volatile long stubbedSize = 1234L;

        @Override public String type() { return TEST_TYPE; }
        @Override public Output handle(ReportJob job) {
            if (throwOnNext) throw new IllegalStateException("simulated handler failure");
            return new Output(stubbedKey, stubbedSize);
        }
    }

    @BeforeEach
    void resetStorageStubsAndJobs() {
        jobs.deleteAll();
        TestableHandler.throwOnNext = false;
        TestableHandler.stubbedKey = "reports/payroll/test/output-" + System.nanoTime() + ".pdf";
        TestableHandler.stubbedSize = 1234L;

        lenient().when(storage.signedGetUrl(eq("reports"), anyString(), any(Duration.class)))
                .thenAnswer(inv -> "https://minio.example/reports/" + inv.getArgument(1)
                        + "?X-Amz-Signature=stubbed");
    }

    @Test
    void enqueue_createsQueuedRow() {
        ReportJob job = reportJobService.enqueue(TEST_TYPE, Map.of("year", 2026, "month", 4));

        assertThat(job.getId()).isNotNull();
        assertThat(job.getStatus()).isEqualTo(ReportJobStatus.QUEUED);
        assertThat(job.getType()).isEqualTo(TEST_TYPE);
        assertThat(job.getParams()).contains("\"year\":2026");
    }

    @Test
    void runner_drainsQueuedJob_marksReadyWithOutputKey() {
        ReportJob enqueued = reportJobService.enqueue(TEST_TYPE, Map.of("year", 2026, "month", 4));

        // Single tick — claims, runs, persists READY.
        runner.runOne(enqueued.getId());

        ReportJob done = jobs.findById(enqueued.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(ReportJobStatus.READY);
        assertThat(done.getOutputKey()).isEqualTo(TestableHandler.stubbedKey);
        assertThat(done.getOutputSize()).isEqualTo(TestableHandler.stubbedSize);
        assertThat(done.getStartedAt()).isNotNull();
        assertThat(done.getCompletedAt()).isNotNull();
    }

    @Test
    void signedDownloadUrl_returnsSignedUrl_onlyAfterReady() {
        ReportJob enqueued = reportJobService.enqueue(TEST_TYPE, Map.of());

        // QUEUED → no URL.
        assertThat(reportJobService.signedDownloadUrl(enqueued)).isNull();

        runner.runOne(enqueued.getId());
        ReportJob ready = jobs.findById(enqueued.getId()).orElseThrow();

        String url = reportJobService.signedDownloadUrl(ready);
        assertThat(url).isNotBlank();
        assertThat(url).contains("X-Amz-Signature");
        assertThat(url).contains(ready.getOutputKey());
    }

    @Test
    void runner_handlerThrows_marksFailedWithErrorMessage() {
        TestableHandler.throwOnNext = true;
        ReportJob enqueued = reportJobService.enqueue(TEST_TYPE, Map.of());

        runner.runOne(enqueued.getId());

        ReportJob failed = jobs.findById(enqueued.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ReportJobStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("HANDLER_FAILED");
        assertThat(failed.getErrorMessage()).contains("simulated handler failure");
        assertThat(reportJobService.signedDownloadUrl(failed)).isNull();
    }

    @Test
    void runner_unknownType_marksFailedWithUnknownTypeCode() {
        ReportJob row = new ReportJob();
        row.setType("NOT_REGISTERED_TYPE");
        row.setParams("{}");
        row.setStatus(ReportJobStatus.QUEUED);
        row.setRequestedBy("test");
        ReportJob saved = jobs.save(row);

        runner.runOne(saved.getId());

        ReportJob failed = jobs.findById(saved.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ReportJobStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("UNKNOWN_TYPE");
    }

    @Test
    void runner_doubleClaim_isANoop_secondCallReturnsClean() {
        ReportJob enqueued = reportJobService.enqueue(TEST_TYPE, Map.of());

        runner.runOne(enqueued.getId());
        // Second call — already READY; tryClaim returns null; nothing changes.
        runner.runOne(enqueued.getId());

        List<ReportJob> all = jobs.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(ReportJobStatus.READY);
    }
}
