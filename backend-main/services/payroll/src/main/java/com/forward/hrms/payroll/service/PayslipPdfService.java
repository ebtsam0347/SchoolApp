package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.payroll.config.PayrollProperties;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipAttendanceInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLeaveInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLeaveLine;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLineItem;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipWithEmployee;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.service.composite.PayrollCompositesService;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Renders the payslip PDF via JasperReports. The compiled
 * {@link JasperReport} is loaded once at startup.
 *
 * <p>Data source is the run-detail row — the exact {@link PayslipWithEmployee}
 * that {@code GET /payroll/runs/{id}/detail} returns
 * ({@link PayrollCompositesService#runDetailRows}): employee profile, bank,
 * zero-filled earning/deduction heads, advance lines, attendance and leave.
 * The PDF only formats it, so the PDF and the run-detail screen can't drift.
 *
 * <p>Idempotent: a payslip whose {@code pdf_object_key} is already set is
 * skipped. Safe to retry the saga step after a partial failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayslipPdfService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final String COMPANY_NAME = "The Spirit of Islam School";
    private static final String COMPANY_ADDRESS = "Sialkot, Punjab, Pakistan";
    /** Optional letterhead logo; the template draws an "SOI" monogram without it. */
    private static final String LOGO_RESOURCE = "jasper/logo.png";
    private static final String CASUAL_LEAVE_CODE = "CASUAL";
    private static final String MEDICAL_LEAVE_CODE = "MEDICAL_M1";

    private final PayrollProperties props;
    private final ObjectStorage storage;
    private final PayslipRepository payslips;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final PayrollRunRepository runs;
    /**
     * Lazy: PayrollCompositesService → PayrollRunService → SagaService →
     * SagaRegistry → PayrollRunPostSaga → this. A direct constructor
     * dependency closes that loop and fails context start.
     */
    private final ObjectProvider<PayrollCompositesService> composites;
    /**
     * Per-payslip tiny tx wrapper. Avoids self-injection (which Lombok's
     * generated constructor doesn't propagate {@code @Lazy} through and
     * causes a circular bean ref) and the proxy-bypass that direct
     * {@code this.markGenerated()} calls would suffer.
     */
    private final org.springframework.transaction.support.TransactionTemplate tx;
    /** REQUIRES_NEW runner for live progress_count pulses — commits
     *  independently of the render loop so the FE sees the counter tick. */
    private final BatchRunner batch;

    /** PDF progress pulse cadence — every this-many payslips rendered. */
    private static final int PROGRESS_CHUNK = 25;

    private JasperReport compiled;
    private JasperReport compiledUrdu;
    private JasperReport compiledSheet;
    private JasperReport compiledSheetUrdu;
    private java.awt.Image logo;

    /**
     * Line rows per slip on the three-per-page sheet. Fixed so every slip is
     * the same height: 8 × 11pt in English, 5 × 17pt in Urdu (Nastaliq needs
     * the taller line). Change together with the band heights in the jrxml.
     */
    static final int SHEET_ROWS = 8;
    static final int SHEET_ROWS_UR = 5;
    private static final String OTHERS_CODE = "_OTHERS";
    private static final String OTHERS_UR = "دیگر";

    /**
     * Urdu names for the standard salary heads on the Urdu slip; a head
     * missing here prints its English catalog name.
     */
    static final Map<String, String> URDU_LINE_NAMES = Map.ofEntries(
            Map.entry("BASIC", "بنیادی تنخواہ"),
            Map.entry("OT", "اوور ٹائم"),
            Map.entry("MEAL", "کھانا الاؤنس"),
            Map.entry("TRAVEL", "سفری الاؤنس"),
            Map.entry("EDUCATION", "تعلیمی الاؤنس"),
            Map.entry("SPECIAL", "خصوصی الاؤنس"),
            Map.entry("SENIORITY", "سینیارٹی الاؤنس"),
            Map.entry("ACCOMMODATION", "رہائشی الاؤنس"),
            Map.entry("HRA", "مکان کرایہ الاؤنس"),
            Map.entry("CONVEYANCE", "سواری الاؤنس"),
            Map.entry("MEDICAL", "طبی الاؤنس"),
            Map.entry("HELD_SALARY_RELEASE", "روکی گئی تنخواہ کی ادائیگی"),
            Map.entry("ANNUAL_LEAVE", "سالانہ چھٹیوں کی رقم"),
            Map.entry("INCOME_TAX", "انکم ٹیکس"),
            Map.entry("EOBI", "ای او بی آئی"),
            Map.entry("RENT", "کرایہ"),
            Map.entry("CANTEEN", "کینٹین"),
            Map.entry("FAIR_PRICE_SHOP", "فیئر پرائس شاپ"),
            Map.entry("UNION_FUND", "یونین فنڈ"),
            Map.entry("PERMANENT_ADVANCE", "مستقل ایڈوانس"),
            Map.entry("SHORT_TERM_ADVANCE", "عارضی ایڈوانس"),
            Map.entry("RFD_ADVANCE", "آر ایف ڈی ایڈوانس"),
            Map.entry("RFD_RECOVERY", "آر ایف ڈی کٹوتی"));

    @PostConstruct
    void compileTemplate() {
        try (InputStream in = new ClassPathResource("jasper/payslip.jrxml").getInputStream()) {
            this.compiled = JasperCompileManager.compileReport(in);
        } catch (IOException | JRException ex) {
            throw new IllegalStateException("Failed to compile jasper/payslip.jrxml", ex);
        }
        try (InputStream in = new ClassPathResource("jasper/payslip-ur.jrxml").getInputStream()) {
            this.compiledUrdu = JasperCompileManager.compileReport(in);
        } catch (IOException | JRException ex) {
            throw new IllegalStateException("Failed to compile jasper/payslip-ur.jrxml", ex);
        }
        this.compiledSheet = compile("jasper/payslip-sheet.jrxml");
        this.compiledSheetUrdu = compile("jasper/payslip-sheet-ur.jrxml");
        ClassPathResource logoResource = new ClassPathResource(LOGO_RESOURCE);
        if (logoResource.exists()) {
            try (InputStream in = logoResource.getInputStream()) {
                this.logo = ImageIO.read(in);
            } catch (IOException ex) {
                log.warn("Payslip logo {} unreadable; rendering without it", LOGO_RESOURCE, ex);
            }
        }
    }

    private static JasperReport compile(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return JasperCompileManager.compileReport(in);
        } catch (IOException | JRException ex) {
            throw new IllegalStateException("Failed to compile " + resource, ex);
        }
    }

    /**
     * Generates PDFs only for payslips whose {@code pdf_object_key} is null.
     * Emits one {@code payroll.payslip.generated} event per newly-generated
     * payslip.
     *
     * <p>§6.12 — the JasperReports render + MinIO upload happen OUTSIDE any
     * transaction. Each payslip's {@code pdf_object_key} + outbox event is
     * persisted inside its own tiny per-payslip {@code @Transactional}
     * ({@link #markGenerated}) so the run-long loop doesn't pin a Hikari
     * connection for the duration of N PDF renders. Closes the "100s of
     * seconds holding a DB connection" issue called out in payroll.md.
     */
    public void generateAll(PayrollRun run) {
        generateAll(run, null);
    }

    /**
     * Saga-thread overload. Pass an {@code X-User-*} snapshot from the
     * kickoff caller (saga payload {@code identity} field). Without it
     * the bulk {@code /employees/by-ids} call to employee-service hits 403
     * — same for the grade catalog and attendance's leave summary.
     */
    public void generateAll(PayrollRun run, Map<String, String> identity) {
        List<Payslip> all = payslips.findAllByPayrollRunId(run.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        // Run-detail rows for the whole run, bulk-built once.
        Map<Long, PayslipWithEmployee> rows = composites.getObject().runDetailRows(run.getId(), identity)
                .stream().collect(Collectors.toMap(PayslipWithEmployee::id, Function.identity()));
        String generatedBy = currentUserLabel();

        // Snapshot progress denominators on the run row before iterating.
        // total = every payslip on the run (so % completion makes sense
        // across saga retries); progress starts at the count of slips that
        // already have a PDF (a partial earlier attempt). FE renders
        // "Generating PDFs 412 / 5,012".
        int total = all.size();
        int alreadyDone = (int) all.stream().filter(p -> p.getPdfObjectKey() != null).count();
        long runId = run.getId();
        batch.runInNewTxVoid(() -> runs.findById(runId).ifPresent(r -> {
            r.setTotalCount(total);
            r.setProgressCount(alreadyDone);
        }));

        // §2.12 — per-row partial-failure: a Jasper render fault or a
        // MinIO upload error on one payslip doesn't stop the others; the
        // failures are counted and thrown after the loop. Not using
        // BatchRunner.runWithPartial because that would re-wrap the
        // render + upload in a transaction and reverse §6.12 (the whole
        // point of which is keeping the Hikari connection out of the
        // PDF-render path).
        int generated = 0;
        int failed = 0;
        String firstError = null;
        int processed = alreadyDone;
        for (Payslip p : all) {
            if (p.getPdfObjectKey() != null) continue;
            try {
                byte[] pdf = render(p, rows.get(p.getId()), generatedBy);
                String key = "payslips/%d/%02d/%s.pdf".formatted(
                        run.getPeriodYear(), run.getPeriodMonth(), p.getEmployeeCode());
                storage.upload(props.payslips().bucket(), key,
                        new ByteArrayInputStream(pdf), pdf.length, "application/pdf", null);
                // Per-payslip tiny tx: UPDATE + outbox emit via TransactionTemplate.
                long payslipId = p.getId();
                tx.executeWithoutResult(status -> markGenerated(payslipId, key, run));
                generated++;
            } catch (RuntimeException ex) {
                failed++;
                if (firstError == null) {
                    firstError = "payslip " + p.getId() + " (employee " + p.getEmployeeId() + "): " + describe(ex);
                }
                log.warn("PDF render/upload failed for payslip {} (employee {}): {}",
                        p.getId(), p.getEmployeeId(), ex.toString(), ex);
            }
            processed++;
            if (processed % PROGRESS_CHUNK == 0) {
                int pulse = processed;
                batch.runInNewTxVoid(() ->
                        runs.findById(runId).ifPresent(r -> r.setProgressCount(pulse)));
            }
        }
        // Final flush — the chunk pulse may have stopped before the last
        // tail of payslips (e.g. 5,003 processed when CHUNK=25). Without
        // this, the FE's "X / total" sticks at 5,000 even though the run
        // is finished.
        int finalProcessed = processed;
        batch.runInNewTxVoid(() ->
                runs.findById(runId).ifPresent(r -> r.setProgressCount(finalProcessed)));
        if (failed > 0) {
            // Fail the caller (the post saga step → retry with this as
            // lastError, FAILED once the budget is spent) instead of letting
            // the run reach POSTED with payslips that have no PDF. Payslips
            // that did succeed keep their key, so a retry only redoes these.
            throw new IllegalStateException(failed + " of " + (total - alreadyDone)
                    + " payslip PDFs failed for payroll run " + runId + " (" + generated + " ok); first: " + firstError);
        }
    }

    /**
     * Admin repair for a POSTED run whose payslips have no PDF in MinIO —
     * a post that ran while uploads were failing, or a key recorded for an
     * object that isn't in the bucket. Clears any key whose object is
     * missing, then generates every missing PDF. Idempotent: a run whose
     * PDFs are all in place does no work. Throws like {@link #generateAll}
     * if any PDF still fails. Request thread; identity forwarded by the
     * commons-web interceptor.
     *
     * <p>{@code force} additionally re-renders every PDF that does exist,
     * overwriting the object at its recorded key with the current template.
     * For runs posted before a template change: the stored PDFs keep their
     * old layout forever otherwise, because generation skips any payslip
     * that already has a key. The key and {@code generated_at} are left
     * alone and no {@code payslip.generated} event is re-published, so
     * employees are not notified a second time.
     */
    public void regenerateMissing(long runId, boolean force) {
        PayrollRun run = runs.findById(runId).orElseThrow(() ->
                new NotFoundError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND, "payroll run " + runId + " not found"));
        if (run.getStatus() != PayrollRunStatus.POSTED) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "regenerate-pdfs requires POSTED (current: " + run.getStatus() + ")");
        }
        String bucket = props.payslips().bucket();
        int cleared = 0;
        for (Payslip p : payslips.findAllByPayrollRunId(runId,
                org.springframework.data.domain.Pageable.unpaged()).getContent()) {
            if (p.getPdfObjectKey() == null || storage.stat(bucket, p.getPdfObjectKey()).isPresent()) continue;
            long payslipId = p.getId();
            batch.runInNewTxVoid(() -> payslips.findById(payslipId).ifPresent(ps -> {
                ps.setPdfObjectKey(null);
                ps.setGeneratedAt(null);
            }));
            cleared++;
        }
        int rerendered = force ? rerenderExisting(run) : 0;
        generateAll(run, null);
        int stale = cleared;
        tx.executeWithoutResult(status -> audit.emit("payroll_run", String.valueOf(runId), "pdfs_regenerated",
                null, Map.of("staleKeysCleared", stale, "force", force, "rerendered", rerendered), false));
    }

    /**
     * Re-renders every payslip of the run that has a PDF key and uploads it
     * over the same object. One payslip in memory at a time; no DB write, no
     * event. Throws after the loop if any payslip failed, like
     * {@link #generateAll}.
     */
    private int rerenderExisting(PayrollRun run) {
        List<Payslip> withKey = payslips.findAllByPayrollRunId(run.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .filter(p -> p.getPdfObjectKey() != null)
                .toList();
        Map<Long, PayslipWithEmployee> rows = composites.getObject().runDetailRows(run.getId(), null)
                .stream().collect(Collectors.toMap(PayslipWithEmployee::id, Function.identity()));
        String generatedBy = currentUserLabel();
        String bucket = props.payslips().bucket();
        int done = 0;
        int failed = 0;
        String firstError = null;
        for (Payslip p : withKey) {
            try {
                byte[] pdf = render(p, rows.get(p.getId()), generatedBy);
                storage.upload(bucket, p.getPdfObjectKey(),
                        new ByteArrayInputStream(pdf), pdf.length, "application/pdf", null);
                done++;
            } catch (RuntimeException ex) {
                failed++;
                if (firstError == null) {
                    firstError = "payslip " + p.getId() + " (employee " + p.getEmployeeId() + "): " + describe(ex);
                }
                log.warn("PDF re-render failed for payslip {} (employee {}): {}",
                        p.getId(), p.getEmployeeId(), ex.toString(), ex);
            }
        }
        log.info("Payroll run {} forced PDF re-render: {} ok, {} failed", run.getId(), done, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " of " + withKey.size()
                    + " payslip PDFs failed to re-render for payroll run " + run.getId()
                    + " (" + done + " ok); first: " + firstError);
        }
        return done;
    }

    /** Exception + root cause in one line — the wrapper alone ("Failed to upload …") hides why. */
    private static String describe(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String head = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return root == ex ? head : head + " <- " + root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    /**
     * Per-payslip tiny tx body — invoked via {@link #tx}. Idempotent on the
     * row's pdf_object_key; the outer loop already filters but a retry
     * would otherwise double-publish.
     */
    void markGenerated(long payslipId, String key, PayrollRun run) {
        Payslip ps = payslips.findById(payslipId).orElse(null);
        if (ps == null || ps.getPdfObjectKey() != null) return;
        ps.setPdfObjectKey(key);
        ps.setGeneratedAt(LocalDateTime.now());
        outbox.publish(PayrollEvents.TOPIC, "payslip:" + ps.getId(),
                PayrollEvents.PAYSLIP_GENERATED,
                new PayrollEvents.PayslipGenerated(ps.getId(), ps.getEmployeeId(),
                        run.getFiscalYearCode(),
                        run.getPeriodYear() + "-" + String.format("%02d", run.getPeriodMonth()),
                        ps.getNet(), key));
    }

    /**
     * Every payslip of a run in one PDF, one payslip per page, ordered by
     * employee code — the same rows as {@code /payroll/runs/{id}/detail}.
     * Rendered on demand (any run status, DRAFT/HELD included); never
     * touches MinIO. Request thread: identity is forwarded by the
     * commons-web interceptor.
     */
    public byte[] renderRun(long runId) {
        return renderPayslips(runId, null);
    }

    /**
     * {@link #renderRun} for a subset: only the payslips of {@code employeeIds},
     * in that order (accounts report A1 prints the filtered, card-sorted
     * list). {@code null} means every payslip of the run, by employee code.
     */
    public byte[] renderPayslips(long runId, List<Long> employeeIds) {
        return renderPayslips(runId, employeeIds, false);
    }

    /** {@link #renderPayslips(long, List)} in English or, with {@code urdu}, on the Urdu slip (payslip-ur.jrxml). */
    public byte[] renderPayslips(long runId, List<Long> employeeIds, boolean urdu) {
        List<PayslipWithEmployee> rows = printRows(runId, employeeIds);
        Map<Long, Payslip> byId = payslipsById(runId);
        String generatedBy = currentUserLabel();

        List<JasperPrint> prints = new ArrayList<>(rows.size());
        for (PayslipWithEmployee row : rows) prints.add(fill(byId.get(row.id()), row, generatedBy, urdu));
        return export(runId, prints, "run-payslips.run", rows.size());
    }

    /**
     * {@link #renderPayslips(long, List, boolean)} on the compact sheet
     * (payslip-sheet.jrxml / payslip-sheet-ur.jrxml): three slips per A4 with
     * a cut line, for bulk printing. Same rows, same values; only zero-amount
     * heads are left off.
     */
    public byte[] renderSheet(long runId, List<Long> employeeIds, boolean urdu) {
        List<PayslipWithEmployee> rows = printRows(runId, employeeIds);
        Map<Long, Payslip> byId = payslipsById(runId);
        String generatedBy = currentUserLabel();
        int lineRows = urdu ? SHEET_ROWS_UR : SHEET_ROWS;

        List<Map<String, ?>> data = new ArrayList<>(rows.size() * lineRows);
        for (int i = 0; i < rows.size(); i++) {
            PayslipWithEmployee row = rows.get(i);
            Map<String, Object> slip = slipValues(byId.get(row.id()), row, generatedBy);
            slip.put("slipNo", i);
            for (Map<String, Object> line : sheetLines(row.earnings().lines(), row.deductions().lines(), lineRows, urdu)) {
                line.putAll(slip);
                data.add(line);
            }
        }
        Map<String, Object> params = new HashMap<>();
        params.put("companyName", COMPANY_NAME);
        params.put("companyAddress", COMPANY_ADDRESS);
        try {
            JasperPrint print = JasperFillManager.fillReport(urdu ? compiledSheetUrdu : compiledSheet, params,
                    new JRMapCollectionDataSource(data));
            return export(runId, List.of(print), "run-payslips-sheet.run", rows.size());
        } catch (JRException ex) {
            throw new IllegalStateException("Jasper render failed for payroll run " + runId, ex);
        }
    }

    private byte[] export(long runId, List<JasperPrint> prints, String action, int payslipCount) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(SimpleExporterInput.getInstance(prints));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            exporter.exportReport();
            // Render stays outside any tx (§6.12); the audit row gets its own tiny one.
            tx.executeWithoutResult(status -> audit.emit("report", String.valueOf(runId), action,
                    null, Map.of("payrollRunId", runId, "payslipCount", payslipCount), false));
            return out.toByteArray();
        } catch (JRException ex) {
            throw new IllegalStateException("Jasper render failed for payroll run " + runId, ex);
        }
    }

    private Map<Long, Payslip> payslipsById(long runId) {
        return payslips.findAllByPayrollRunId(runId,
                        org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .collect(Collectors.toMap(Payslip::getId, Function.identity()));
    }

    /** The run-detail rows to print: all of the run by employee code, or {@code employeeIds} in that order. */
    private List<PayslipWithEmployee> printRows(long runId, List<Long> employeeIds) {
        if (!runs.existsById(runId)) {
            throw new NotFoundError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND, "payroll run " + runId + " not found");
        }
        List<PayslipWithEmployee> all = composites.getObject().runDetailRows(runId, null);
        List<PayslipWithEmployee> rows;
        if (employeeIds == null) {
            rows = all;
        } else {
            Map<Long, PayslipWithEmployee> byEmployee = all.stream()
                    .collect(Collectors.toMap(r -> r.employee().employeeId(), Function.identity(), (a, b) -> a));
            rows = employeeIds.stream().distinct().map(byEmployee::get).filter(java.util.Objects::nonNull).toList();
        }
        if (rows.isEmpty()) {
            throw new NotFoundError(PayrollErrorCode.PAYSLIP_NOT_FOUND, "payroll run " + runId + " has no payslips");
        }
        return rows;
    }

    private byte[] render(Payslip p, PayslipWithEmployee row, String generatedBy) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            JasperExportManager.exportReportToPdfStream(fill(p, row, generatedBy), out);
            return out.toByteArray();
        } catch (JRException ex) {
            throw new IllegalStateException("Jasper render failed for payslip " + p.getId(), ex);
        }
    }

    /**
     * Maps one run-detail row onto payslip.jrxml. Only {@code otHours} comes
     * off the entity (plus paid/LOP days when the row has no attendance) —
     * the run-detail row doesn't carry it.
     */
    private JasperPrint fill(Payslip p, PayslipWithEmployee row, String generatedBy) {
        return fill(p, row, generatedBy, false);
    }

    private JasperPrint fill(Payslip p, PayslipWithEmployee row, String generatedBy, boolean urdu) {
        if (row == null) {
            throw new IllegalStateException("no run-detail row for payslip " + (p == null ? null : p.getId()));
        }
        Map<String, Object> params = slipValues(p, row, generatedBy);
        params.put("companyName", COMPANY_NAME);
        params.put("companyAddress", COMPANY_ADDRESS);
        params.put("logo", logo);

        // Earnings and deductions side by side, one table row per index;
        // names/codes are escaped for the template's styled markup.
        // Jasper's bundled BeanUtils can't read record accessors, so rows go
        // in as maps (CLAUDE.md gotcha).
        List<PayslipLineItem> earnings = row.earnings().lines();
        List<PayslipLineItem> deductions = row.deductions().lines();
        int count = Math.max(earnings.size(), deductions.size());
        List<Map<String, ?>> table = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("earningName", i < earnings.size() ? styledSafe(earnings.get(i).name()) : "");
            r.put("earningCode", i < earnings.size() ? lineCode(earnings.get(i)) : "");
            r.put("earningAmountText", i < earnings.size() ? fmt(earnings.get(i).amount()) : "");
            r.put("deductionName", i < deductions.size() ? styledSafe(deductions.get(i).name()) : "");
            r.put("deductionCode", i < deductions.size() ? lineCode(deductions.get(i)) : "");
            r.put("deductionAmountText", i < deductions.size() ? fmt(deductions.get(i).amount()) : "");
            if (urdu) {
                PayslipLineItem e = i < earnings.size() ? earnings.get(i) : null;
                PayslipLineItem d = i < deductions.size() ? deductions.get(i) : null;
                r.put("earningNameUr", urduName(e));
                r.put("earningNameEn", e == null || urduName(e) != null ? null : e.name());
                r.put("deductionNameUr", urduName(d));
                r.put("deductionNameEn", d == null || urduName(d) != null ? null : d.name());
            }
            table.add(r);
        }

        try {
            return JasperFillManager.fillReport(urdu ? compiledUrdu : compiled, params,
                    table.isEmpty() ? new JREmptyDataSource(1) : new JRMapCollectionDataSource(table));
        } catch (JRException ex) {
            throw new IllegalStateException("Jasper render failed for payslip " + row.id(), ex);
        }
    }

    /**
     * Every per-slip value both layouts print — header, employee, attendance,
     * leave, totals — keyed by template name. The full slip takes them as
     * parameters, the sheet as fields repeated on each of its line rows.
     */
    private Map<String, Object> slipValues(Payslip p, PayslipWithEmployee row, String generatedBy) {
        var emp = row.employee();
        var salary = row.salary();

        Map<String, Object> params = new HashMap<>();
        params.put("monthLabel", "Pay Period: "
                + Month.of(row.periodMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + row.periodYear());
        params.put("fiscalYear", row.fiscalYearCode());
        params.put("heldBanner", "HELD".equals(row.status())
                ? "AMOUNT HELD — NOT DISBURSED" + (row.heldReason() == null ? "" : " (" + row.heldReason() + ")")
                : "");
        // Urdu slip: period value without the English caption, and the held
        // banner in Urdu (the free-text reason stays on the English slip).
        params.put("periodText", Month.of(row.periodMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + row.periodYear());
        params.put("heldBannerUr", "HELD".equals(row.status()) ? "رقم روکی گئی ہے، ادا نہیں کی گئی" : "");

        params.put("employeeCode", str(emp.employeeCode()));
        params.put("employeeName", str(emp.fullName()));
        params.put("fatherName", str(emp.fatherName()));
        params.put("designation", str(emp.designation()));
        params.put("department", str(emp.department()));
        params.put("grade", str(emp.grade()));
        params.put("dateOfJoining", emp.dateOfJoining() == null ? "-" : DATE.format(emp.dateOfJoining()));
        params.put("bankName", str(row.bank() == null ? null : row.bank().bankName()));
        params.put("bankAccount", str(row.bank() == null ? null : row.bank().accountNumber()));
        params.put("advanceBalanceText", salary.permanentAdvanceBalance() == null
                ? "-" : fmt(salary.permanentAdvanceBalance()));

        // Attendance from the run-detail row (same block the screen shows);
        // paid days / OT fall back to the persisted payslip when attendance
        // service was unreachable.
        PayslipAttendanceInfo att = row.attendance();
        params.put("workingDaysText", att == null ? "-" : String.valueOf(att.workingDays()));
        params.put("daysWorkedText", att != null && att.paidDays() != null ? fmtQty(att.paidDays())
                : p == null || p.getPaidDays() == null ? "-" : fmtQty(p.getPaidDays()));
        params.put("presentDaysText", att == null ? "-" : fmtQty(att.presentDays()));
        params.put("absentDaysText", att == null ? "-" : fmtQty(att.absentDays()));
        params.put("lopDaysText", att != null ? fmtQty(att.lopDays())
                : p == null ? "-" : fmtQty(p.getLopDays()));
        params.put("paidLeaveDaysText", att == null ? "-" : fmtQty(att.paidLeaveDays()));
        params.put("unpaidLeaveDaysText", att == null ? "-" : fmtQty(att.unpaidLeaveDays()));
        params.put("holidaysText", att == null ? "-" : String.valueOf(att.holidayDays()));
        params.put("weeklyOffText", att == null ? "-" : String.valueOf(att.weeklyOffDays()));
        params.put("overtimeHoursText", p == null ? "-" : fmtQty(p.getOtHours()));

        PayslipLeaveInfo leaves = row.leaves();
        PayslipLeaveLine casual = findLeave(leaves, CASUAL_LEAVE_CODE);
        PayslipLeaveLine medical = findLeave(leaves, MEDICAL_LEAVE_CODE);
        params.put("casualLeavesMonthText", fmtQty(casual == null ? BigDecimal.ZERO : casual.availedThisMonth()));
        params.put("medicalLeavesMonthText", fmtQty(medical == null ? BigDecimal.ZERO : medical.availedThisMonth()));
        params.put("totalCasualLeavesText", fmtQty(casual == null ? BigDecimal.ZERO : casual.availedThisCycle()));
        params.put("totalMedicalLeavesText", fmtQty(medical == null ? BigDecimal.ZERO : medical.availedThisCycle()));
        var al = leaves == null ? null : leaves.annualLeave();
        params.put("annualAvailedText", al == null ? "-" : fmtQty(al.availed()));
        params.put("annualBalanceText", al == null ? "-" : fmtQty(al.balance()));

        params.put("grossSalaryText", fmt(salary.grossSalary()));
        params.put("totalDeductionText", fmt(salary.totalDeduction()));
        params.put("netSalaryText", fmt(salary.netSalary()));
        params.put("netSalaryWords", AmountInWords.of(salary.netSalary()));
        params.put("generatedAt", STAMP.format(row.generatedAt() == null ? LocalDateTime.now() : row.generatedAt()));
        params.put("generatedBy", generatedBy);
        return params;
    }

    /**
     * One slip's line rows for the sheet: zero-amount heads dropped (the
     * run-detail row zero-fills every active head), padded with blank rows
     * to exactly {@code rows} so every slip is the same height, and a side
     * with more heads than fit folds its tail into one "Others" line.
     */
    static List<Map<String, Object>> sheetLines(List<PayslipLineItem> earnings, List<PayslipLineItem> deductions,
                                                int rows, boolean urdu) {
        List<PayslipLineItem> e = foldTail(nonZero(earnings), rows);
        List<PayslipLineItem> d = foldTail(nonZero(deductions), rows);
        List<Map<String, Object>> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Map<String, Object> r = new HashMap<>();
            putSheetLine(r, "earning", i < e.size() ? e.get(i) : null, urdu);
            putSheetLine(r, "deduction", i < d.size() ? d.get(i) : null, urdu);
            out.add(r);
        }
        return out;
    }

    private static void putSheetLine(Map<String, Object> r, String side, PayslipLineItem line, boolean urdu) {
        if (line == null) return;
        r.put(side + "AmountText", fmt(line.amount()));
        if (!urdu) {
            r.put(side + "Name", line.name());
            return;
        }
        String ur = OTHERS_CODE.equals(line.code()) ? OTHERS_UR : urduName(line);
        r.put(side + "NameUr", ur);
        r.put(side + "NameEn", ur == null ? line.name() : null);
    }

    private static List<PayslipLineItem> nonZero(List<PayslipLineItem> lines) {
        return lines == null ? List.of()
                : lines.stream().filter(l -> l.amount() != null && l.amount().signum() != 0).toList();
    }

    private static List<PayslipLineItem> foldTail(List<PayslipLineItem> lines, int rows) {
        if (lines.size() <= rows) return lines;
        BigDecimal rest = lines.subList(rows - 1, lines.size()).stream()
                .map(PayslipLineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PayslipLineItem> out = new ArrayList<>(lines.subList(0, rows - 1));
        out.add(new PayslipLineItem(OTHERS_CODE, "Others", rest));
        return out;
    }

    /** Urdu name of a salary head by code (or by name, for heads like "EOBI" with no code); null when untranslated. */
    static String urduName(PayslipLineItem line) {
        if (line == null) return null;
        String byCode = line.code() == null ? null : URDU_LINE_NAMES.get(line.code().toUpperCase(Locale.ROOT));
        if (byCode != null) return byCode;
        return line.name() == null ? null : URDU_LINE_NAMES.get(line.name().toUpperCase(Locale.ROOT));
    }

    /** Code under the line name, dropped when it just repeats the name ("EOBI"). */
    private static String lineCode(PayslipLineItem line) {
        String code = line.code();
        return code == null || code.equalsIgnoreCase(line.name()) ? "" : styledSafe(code);
    }

    /** Line cells use markup="styled" (grey code suffix); escape catalog text so "R&D" can't break it. */
    private static String styledSafe(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static PayslipLeaveLine findLeave(PayslipLeaveInfo info, String code) {
        if (info == null || info.leaves() == null) return null;
        return info.leaves().stream().filter(l -> code.equals(l.code())).findFirst().orElse(null);
    }

    /** Display name of whoever triggered the render (saga replays the kickoff caller). */
    private static String currentUserLabel() {
        return CurrentUserHolder.get()
                .map(u -> u.fullName() != null && !u.fullName().isBlank() ? u.fullName() : u.username())
                .orElse(null);
    }

    private static String str(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    /** Days/hours without a trailing ".00" for whole numbers. */
    static String fmtQty(BigDecimal n) {
        if (n == null) return "-";
        if (n.signum() == 0) return "0";
        return n.stripTrailingZeros().toPlainString();
    }

    /** Format a rupee amount as a comma-grouped string ({@code "1,234.50"}). */
    static String fmt(BigDecimal rupees) {
        if (rupees == null) return "";
        BigDecimal scaled = rupees.setScale(2, RoundingMode.HALF_UP);
        return scaled.toPlainString().replaceAll(
                "(?<=\\d)(?=(\\d{3})+\\.)", ",");
    }

}
