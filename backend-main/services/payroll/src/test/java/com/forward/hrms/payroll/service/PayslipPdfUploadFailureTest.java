package com.forward.hrms.payroll.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.StepResult;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.payroll.config.PayrollProperties;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipDeductions;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipEarnings;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipEmployeeInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLineItem;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipSalaryInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipWithEmployee;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.service.composite.PayrollCompositesService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A MinIO upload failure (e.g. a wrong access key → InvalidAccessKeyId)
 * must surface on the post saga — retry with lastError, then FAILED — and
 * never let GENERATE_PDFS return Done. Pure unit: no DB, no MinIO.
 */
class PayslipPdfUploadFailureTest {

    private static final long RUN_ID = 135L;

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayrollCompositesService composites = mock(PayrollCompositesService.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private PayslipPdfService pdfService;
    private PayrollRun run;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<PayrollCompositesService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(composites);
        pdfService = new PayslipPdfService(new PayrollProperties(new PayrollProperties.Payslips("payslips", null), null),
                storage, payslips, mock(Outbox.class), mock(AuditEmitter.class), runs, provider, tx,
                mock(BatchRunner.class));
        pdfService.compileTemplate();

        run = new PayrollRun();
        ReflectionTestUtils.setField(run, "id", RUN_ID);
        run.setFiscalYearCode("2026-27");
        run.setPeriodYear(2026);
        run.setPeriodMonth(9);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));

        Payslip p = new Payslip();
        ReflectionTestUtils.setField(p, "id", 23454L);
        p.setPayrollRunId(RUN_ID);
        p.setEmployeeId(3094L);
        p.setEmployeeCode("13411");
        p.setStatus(PayslipStatus.DRAFT);
        when(payslips.findAllByPayrollRunId(eq(RUN_ID), any())).thenReturn(new PageImpl<>(List.of(p)));
        when(composites.runDetailRows(eq(RUN_ID), any())).thenReturn(List.of(row(23454L)));
    }

    @Test
    void uploadFailure_throwsWithRootCause_andRecordsNoKey() {
        doThrow(new ObjectStorage.StorageException("Failed to upload payslips/payslips/2026/09/13411.pdf",
                new RuntimeException("The Access Key Id you provided does not exist in our records.")))
                .when(storage).upload(anyString(), anyString(), any(), anyLong(), any(), any());

        assertThatThrownBy(() -> pdfService.generateAll(run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 of 1 payslip PDFs failed")
                .hasMessageContaining("payslip 23454")
                .hasMessageContaining("Access Key Id you provided does not exist");
        verify(tx, never()).executeWithoutResult(any());   // markGenerated never ran → pdf_object_key stays null
    }

    @Test
    void successfulUpload_doesNotThrow_andRecordsTheKey() {
        pdfService.generateAll(run);

        verify(storage).upload(eq("payslips"), eq("payslips/2026/09/13411.pdf"), any(), anyLong(),
                eq("application/pdf"), any());
        verify(tx).executeWithoutResult(any());
    }

    @Test
    void postSaga_generatePdfsStep_retriesWithLastErrorInsteadOfDone() {
        PayslipPdfService failingPdf = mock(PayslipPdfService.class);
        doThrow(new IllegalStateException("1 of 1 payslip PDFs failed for payroll run 135 (0 ok); first: "
                + "payslip 23454 (employee 3094): StorageException: Failed to upload"))
                .when(failingPdf).generateAll(any(), any());
        PayrollRunPostSaga saga = new PayrollRunPostSaga(failingPdf, mock(PayrollComputeService.class), runs,
                mock(Outbox.class), mock(AuditEmitter.class));

        ObjectNode payload = JsonNodeFactory.instance.objectNode().put("runId", RUN_ID);
        StepResult result = saga.steps().get(0).execute(new SagaContext(72L, "payroll_run:135", payload, 0));

        assertThat(result).isInstanceOf(StepResult.Retry.class);
        assertThat(((StepResult.Retry) result).reason()).contains("1 of 1 payslip PDFs failed", "payslip 23454");
    }

    private static PayslipWithEmployee row(long payslipId) {
        BigDecimal basic = new BigDecimal("100000.00");
        return new PayslipWithEmployee(payslipId, 2026, 9, "2026-27", "DRAFT", null, null,
                new PayslipEmployeeInfo(3094L, "13411", "Ali Raza", "Muhammad Raza", "Supervisor", "Stitching",
                        null, LocalDate.of(2021, 3, 15), null),
                null,
                new PayslipSalaryInfo(basic, basic, BigDecimal.ZERO, basic, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, null),
                new PayslipEarnings(List.of(new PayslipLineItem("BASIC", "Basic", basic)), basic),
                new PayslipDeductions(List.of(), BigDecimal.ZERO),
                null, null, null, null);
    }
}
