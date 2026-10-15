package com.forward.hrms.payroll.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.IncrementBatch;
import com.forward.hrms.payroll.entity.IncrementBatchLine;
import com.forward.hrms.payroll.entity.ReportJob;
import com.forward.hrms.payroll.repository.IncrementBatchLineRepository;
import com.forward.hrms.payroll.repository.IncrementBatchRepository;

import lombok.extern.slf4j.Slf4j;

import net.sf.jasperreports.engine.JREmptyDataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Increment letters — one PDF per employee in a posted
 * {@link IncrementBatch}, packaged as a ZIP. Async because a 1k-employee
 * batch is many seconds of Jasper fills + zip compression.
 */
@Slf4j
@Service
public class IncrementLettersBatchReportService implements ReportJobHandler {

    public static final String TYPE = "INCREMENT_LETTERS";

    private static final String BUCKET = "reports";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String COMPANY_NAME = "The Spirit of Islam School";
    private static final String SIGNED_BY = "HR Department";

    private final IncrementBatchRepository batches;
    private final IncrementBatchLineRepository lines;
    private final EmployeeClient employees;
    private final JasperRenderer jasper;
    private final ObjectStorage storage;
    private final ObjectMapper json;

    public IncrementLettersBatchReportService(IncrementBatchRepository batches,
                                              IncrementBatchLineRepository lines,
                                              EmployeeClient employees,
                                              JasperRenderer jasper,
                                              ObjectStorage storage,
                                              @Qualifier("outboxObjectMapper") ObjectMapper json) {
        this.batches = batches;
        this.lines = lines;
        this.employees = employees;
        this.jasper = jasper;
        this.storage = storage;
        this.json = json;
    }

    @Override public String type() { return TYPE; }

    @Override
    public Output handle(ReportJob job) {
        long batchId = parseBatchId(job);
        IncrementBatch batch = batches.findById(batchId).orElseThrow(() ->
                new IllegalStateException("increment batch not found: " + batchId));

        List<IncrementBatchLine> all = lines.findAllByBatchIdOrderByEmployeeId(batchId);
        Set<Long> employeeIds = new HashSet<>(all.size());
        for (IncrementBatchLine l : all) employeeIds.add(l.getEmployeeId());
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(employeeIds);

        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        int generated = 0;
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(java.util.zip.Deflater.BEST_SPEED);
            for (IncrementBatchLine line : all) {
                EmployeeClient.EmployeeBrief brief = briefs.getOrDefault(line.getEmployeeId(),
                        EmployeeClient.EmployeeBrief.placeholder(line.getEmployeeId()));
                byte[] pdf = renderOne(batch, line, brief);
                zip.putNextEntry(new ZipEntry("%s_%s.pdf".formatted(brief.employeeCode(), batchId)));
                zip.write(pdf);
                zip.closeEntry();
                generated++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Increment-letter zip failed for batch " + batchId, e);
        }

        byte[] bytes = out.toByteArray();
        String key = "payroll/increment-letters/%d/%d.zip".formatted(batchId, job.getId());
        storage.upload(BUCKET, key,
                new ByteArrayInputStream(bytes), bytes.length,
                "application/zip", null);
        log.info("Generated {} increment letters for batch {} ({} bytes zipped)", generated, batchId, bytes.length);
        return new Output(key, bytes.length);
    }

    private byte[] renderOne(IncrementBatch batch, IncrementBatchLine line, EmployeeClient.EmployeeBrief brief) {
        Map<String, Object> params = new HashMap<>();
        params.put("companyName", COMPANY_NAME);
        params.put("letterDate", DATE.format(LocalDate.now()));
        params.put("employeeName", brief.fullName());
        params.put("employeeCode", brief.employeeCode());
        params.put("designation", "");
        params.put("department", "");
        params.put("oldBasic", fmt(line.getOldBasic()));
        params.put("newBasic", fmt(line.getNewBasic()));
        params.put("incrementText", incrementText(line.getOldBasic(), line.getNewBasic()));
        params.put("effectiveFrom", line.getEffectiveFrom() != null
                ? DATE.format(line.getEffectiveFrom())
                : DATE.format(batch.getEffectiveFrom()));
        params.put("signedBy", SIGNED_BY);
        return jasper.renderPdf("/jasper/increment-letter.jrxml", params, new JREmptyDataSource(1));
    }

    private static String incrementText(BigDecimal oldBasic, BigDecimal newBasic) {
        BigDecimal delta = newBasic.subtract(oldBasic);
        if (oldBasic.signum() == 0) return "Rs " + fmt(delta);
        BigDecimal pct = delta.multiply(BigDecimal.valueOf(100))
                .divide(oldBasic, 2, RoundingMode.HALF_UP);
        return "Rs " + fmt(delta) + " (" + pct.toPlainString() + "%)";
    }

    private static String fmt(BigDecimal rupees) {
        if (rupees == null) return "";
        BigDecimal scaled = rupees.setScale(2, RoundingMode.HALF_UP);
        return scaled.toPlainString().replaceAll("(?<=\\d)(?=(\\d{3})+\\.)", ",");
    }

    private long parseBatchId(ReportJob job) {
        try {
            JsonNode parsed = json.readTree(job.getParams());
            JsonNode id = parsed.get("incrementBatchId");
            if (id == null || !id.canConvertToLong()) {
                throw new IllegalArgumentException(
                        "INCREMENT_LETTERS params missing incrementBatchId (got " + job.getParams() + ")");
            }
            return id.asLong();
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "INCREMENT_LETTERS params not parseable: " + job.getParams(), ex);
        }
    }
}
