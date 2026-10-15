package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.CsvWriter;
import com.forward.hrms.commons.reports.XlsxReport;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.dto.response.SectionDto;
import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.AccountsReportColumnDto;
import com.forward.hrms.payroll.dto.response.AccountsReportDefinitionDto;
import com.forward.hrms.payroll.dto.response.AccountsReportResultDto;
import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.AccountsReportOutput;
import com.forward.hrms.payroll.enums.AccountsReportParam;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.ReportColumnType;
import com.forward.hrms.payroll.enums.ReportExportFormat;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs accounts reports: the report's data source produces payroll-side
 * rows, this class adds employee columns, applies the employee filters and
 * search, sorts (card number by default), numbers, totals and pages — or
 * exports the full filtered result. Totals are always over the full result,
 * so screen and export agree.
 */
@Service
@RequiredArgsConstructor
public class AccountsReportService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 500;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", Locale.ENGLISH);

    private final List<AccountsReportSource> sources;
    private final AccountsReportEmployees employees;
    private final CatalogCache catalog;
    private final AuditEmitter audit;

    /** A finished report run: every filtered row (numbered), totals, employee count and ledger header. */
    record Computed(AccountsReport report, List<Map<String, Object>> rows, Map<String, BigDecimal> totals,
                    long totalEmployees, Map<String, Object> header, String caption,
                    List<Map<String, Object>> groups,
                    /** Employee of each row, same order as {@code rows}; empty for summaries. */
                    List<Long> rowEmployeeIds,
                    /** The report's columns as its source resolved them, without Sr No. */
                    List<AccountsReportColumnDto> columns) {

        /** Columns as shown, Sr No first (except ledgers). */
        List<AccountsReportColumnDto> shownColumns() {
            return AccountsReportDefinitionDto.withSrNo(report, columns);
        }
    }

    public record Exported(String filename, String contentType, byte[] body) {}

    /** Reports that have a data source, in registry order. */
    public List<AccountsReportDefinitionDto> registry() {
        List<AccountsReportDefinitionDto> out = new ArrayList<>();
        for (AccountsReport r : AccountsReport.values()) {
            sources.stream().filter(s -> s.supports(r)).findFirst()
                    .ifPresent(s -> out.add(AccountsReportDefinitionDto.of(r, s.columns(r))));
        }
        return out;
    }

    @Transactional
    public AccountsReportResultDto data(String reportId, AccountsReportFilter filter) {
        AccountsReport r = require(reportId);
        Computed c = run(r, filter);

        int size = filter.size() == null || filter.size() <= 0 ? DEFAULT_PAGE_SIZE
                : Math.min(filter.size(), MAX_PAGE_SIZE);
        int page = filter.page() == null ? 0 : Math.max(0, filter.page());
        int from = (int) Math.min((long) page * size, c.rows().size());
        int to = Math.min(from + size, c.rows().size());
        int totalPages = (c.rows().size() + size - 1) / size;

        if (page == 0) emitAudit(c, "screen");
        return new AccountsReportResultDto(r.id(), r.title(), c.caption(), c.shownColumns(), c.header(),
                c.rows().subList(from, to), c.totals(), c.totalEmployees(), page, size, c.rows().size(), totalPages,
                c.groups());
    }

    @Transactional
    public Exported export(String reportId, AccountsReportFilter filter, ReportExportFormat format) {
        AccountsReport r = require(reportId);
        Computed c = run(r, filter);
        byte[] body = format == ReportExportFormat.XLSX ? xlsx(c) : csv(c);
        emitAudit(c, format.extension());
        String filename = "%s-%s-%s.%s".formatted(r.id(), slug(r.title()), LocalDate.now(), format.extension());
        return new Exported(filename, format.contentType(), body);
    }

    // ---------- run ----------

    /** Validates the filter against the report's resolved columns, then computes. */
    private Computed run(AccountsReport r, AccountsReportFilter f) {
        AccountsReportSource source = sources.stream().filter(s -> s.supports(r)).findFirst()
                .orElseThrow(() -> notFound(r.id()));
        List<AccountsReportColumnDto> columns = source.columns(r);
        AccountsReportFilterValidator.validate(r, f, AccountsReportDefinitionDto.withSrNo(r, columns));
        return compute(r, f, source, columns);
    }

    private Computed compute(AccountsReport r, AccountsReportFilter f, AccountsReportSource source,
                             List<AccountsReportColumnDto> columns) {
        List<AccountsReportRow> raw = source.rows(r, f);

        Set<Long> ids = raw.stream().map(AccountsReportRow::employeeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, AccountsReportEmployee> emps = ids.isEmpty() ? Map.of() : employees.resolve(ids);
        String search = f.search() == null || f.search().isBlank() ? null : f.search().trim().toLowerCase(Locale.ROOT);

        List<Built> built = new ArrayList<>(raw.size());
        for (AccountsReportRow row : raw) {
            AccountsReportEmployee e = emps.getOrDefault(row.employeeId(), AccountsReportEmployee.unknown(row.employeeId()));
            if (!AccountsReportEmployees.matches(r, f, e)) continue;
            if (search != null && !contains(e.cardNo(), search) && !contains(e.name(), search)) continue;
            Map<String, Object> values = new LinkedHashMap<>();
            for (AccountsReportColumnDto col : columns) {
                values.put(col.key(), AccountsReportColumnDto.EMPLOYEE_KEYS.contains(col.key())
                        ? e.value(col.key()) : row.values().get(col.key()));
            }
            built.add(new Built(e, values));
        }

        boolean ledger = r.output() == AccountsReportOutput.LEDGER;
        List<Map<String, Object>> ordered;
        if (r.output() == AccountsReportOutput.SUMMARY) {
            ordered = summarize(columns, built);
            ordered.sort(summaryComparator(columns, f));
        } else {
            Comparator<Built> order = comparator(r, f);
            if (r.groupBy() != null) {
                String g = r.groupBy();
                order = Comparator.<Built, Object>comparing(b -> b.values().get(g), AccountsReportService::compareValues)
                        .thenComparing(order);
            }
            built.sort(order);
            ordered = built.stream().map(Built::values).collect(Collectors.toCollection(ArrayList::new));
        }

        List<Map<String, Object>> rows = new ArrayList<>(ordered.size());
        int n = 0;
        for (Map<String, Object> values : ordered) {
            if (ledger) {
                rows.add(values);
            } else {
                Map<String, Object> numbered = new LinkedHashMap<>();
                numbered.put(AccountsReportColumnDto.SR_NO, ++n);
                numbered.putAll(values);
                rows.add(numbered);
            }
        }

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (AccountsReportColumnDto col : columns) {
            if (!col.totalable()) continue;
            BigDecimal sum = BigDecimal.ZERO;
            for (Map<String, Object> row : rows) {
                if (row.get(col.key()) instanceof BigDecimal v) sum = sum.add(v);
            }
            totals.put(col.key(), sum);
        }
        if (ledger) {
            totals.put("runningBalance", totals.getOrDefault("advancePaid", BigDecimal.ZERO)
                    .subtract(totals.getOrDefault("amountRecovered", BigDecimal.ZERO)));
        }

        Set<Long> distinct = built.stream().map(b -> b.employee().employeeId()).collect(Collectors.toSet());
        Map<String, Object> header = ledger && distinct.size() == 1 ? built.getFirst().employee().header() : null;
        List<Map<String, Object>> groups = r.groupBy() == null ? null : groups(r, columns, built);
        List<Long> rowEmployeeIds = r.output() == AccountsReportOutput.SUMMARY ? List.of()
                : built.stream().map(b -> b.employee().employeeId()).toList();
        return new Computed(r, rows, totals, distinct.size(), header, caption(r, f, source), groups,
                rowEmployeeIds, columns);
    }

    /**
     * Validated, fully filtered run for a PDF document (slip / letter /
     * ledger) — the same rows and totals the screen and export show. Emits
     * the audit row under {@code format} ("pdf").
     */
    Computed prepareDocument(String reportId, AccountsReportFilter filter) {
        Computed c = run(require(reportId), filter);
        emitAudit(c, "pdf");
        return c;
    }

    private record Built(AccountsReportEmployee employee, Map<String, Object> values) {}

    /**
     * SUMMARY reports: one row per distinct combination of the report's
     * text / month columns (e.g. department, or department × payment mode),
     * amounts summed, {@code employeeCount} = distinct employees in the group.
     */
    private static List<Map<String, Object>> summarize(List<AccountsReportColumnDto> columns, List<Built> built) {
        List<AccountsReportColumnDto> keyCols = columns.stream()
                .filter(c -> c.type() == ReportColumnType.TEXT || c.type() == ReportColumnType.MONTH).toList();
        Map<List<Object>, Map<String, Object>> byKey = new LinkedHashMap<>();
        Map<List<Object>, Set<Long>> employeesByKey = new HashMap<>();
        for (Built b : built) {
            List<Object> key = keyCols.stream().map(c -> b.values().get(c.key())).toList();
            Map<String, Object> agg = byKey.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                for (AccountsReportColumnDto c : columns) {
                    m.put(c.key(), c.type() == ReportColumnType.AMOUNT ? BigDecimal.ZERO : b.values().get(c.key()));
                }
                return m;
            });
            employeesByKey.computeIfAbsent(key, k -> new HashSet<>()).add(b.employee().employeeId());
            for (AccountsReportColumnDto c : columns) {
                if (c.type() == ReportColumnType.AMOUNT && b.values().get(c.key()) instanceof BigDecimal v) {
                    agg.put(c.key(), ((BigDecimal) agg.get(c.key())).add(v));
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(byKey.size());
        byKey.forEach((key, agg) -> {
            if (agg.containsKey(EMPLOYEE_COUNT)) agg.put(EMPLOYEE_COUNT, employeesByKey.get(key).size());
            out.add(agg);
        });
        return out;
    }

    static final String EMPLOYEE_COUNT = "employeeCount";

    /** Summary rows by the requested column, else by their text / month columns in display order. */
    private static Comparator<Map<String, Object>> summaryComparator(List<AccountsReportColumnDto> columns,
                                                                    AccountsReportFilter f) {
        String key = f.sortBy();
        if (key != null && !key.isBlank() && !key.equals(AccountsReportColumnDto.SR_NO)) {
            Comparator<Map<String, Object>> c = (a, b) -> compareValues(a.get(key), b.get(key));
            return "desc".equalsIgnoreCase(f.sortDir()) ? c.reversed() : c;
        }
        Comparator<Map<String, Object>> c = (a, b) -> 0;
        for (AccountsReportColumnDto col : columns) {
            if (col.type() != ReportColumnType.TEXT && col.type() != ReportColumnType.MONTH) continue;
            c = c.thenComparing((a, b) -> compareValues(a.get(col.key()), b.get(col.key())));
        }
        return c;
    }

    /** GROUPED_LIST subtotals, in print order: {@code group}, {@code employees}, {@code totals}. */
    private static List<Map<String, Object>> groups(AccountsReport r, List<AccountsReportColumnDto> columns,
                                                    List<Built> sortedBuilt) {
        Map<Object, List<Built>> byGroup = new LinkedHashMap<>();
        for (Built b : sortedBuilt) {
            byGroup.computeIfAbsent(b.values().get(r.groupBy()), k -> new ArrayList<>()).add(b);
        }
        List<Map<String, Object>> out = new ArrayList<>(byGroup.size());
        byGroup.forEach((group, members) -> {
            Map<String, BigDecimal> totals = new LinkedHashMap<>();
            for (AccountsReportColumnDto col : columns) {
                if (!col.totalable()) continue;
                BigDecimal sum = BigDecimal.ZERO;
                for (Built b : members) {
                    if (b.values().get(col.key()) instanceof BigDecimal v) sum = sum.add(v);
                }
                totals.put(col.key(), sum);
            }
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("group", group);
            g.put("employees", members.stream().map(b -> b.employee().employeeId()).distinct().count());
            g.put("rows", members.size());
            g.put("totals", totals);
            out.add(g);
        });
        return out;
    }

    /** Card number (numeric) by default; any column when asked. Stable, so a ledger keeps its date order. */
    private static Comparator<Built> comparator(AccountsReport r, AccountsReportFilter f) {
        Comparator<Built> byCard = (a, b) -> compareCards(a.employee().cardNo(), b.employee().cardNo());
        String key = f.sortBy();
        if (key == null || key.isBlank() || key.equals(AccountsReportColumnDto.SR_NO)
                || r.output() == AccountsReportOutput.LEDGER) {
            return byCard;
        }
        Comparator<Built> byColumn = key.equals(AccountsReportColumnDto.CARD_NO) ? byCard
                : (a, b) -> compareValues(a.values().get(key), b.values().get(key));
        if ("desc".equalsIgnoreCase(f.sortDir())) byColumn = byColumn.reversed();
        return byColumn.thenComparing(byCard);
    }

    static int compareCards(String a, String b) {
        Long na = AccountsReportEmployees.numericCard(a);
        Long nb = AccountsReportEmployees.numericCard(b);
        if (na != null && nb != null) return Long.compare(na, nb);
        if (na != null) return -1;
        if (nb != null) return 1;
        return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString()));
        }
        if (a instanceof String x && b instanceof String y) return x.compareToIgnoreCase(y);
        if (a instanceof Comparable x && a.getClass() == b.getClass()) return x.compareTo(b);
        return a.toString().compareToIgnoreCase(b.toString());
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    // ---------- caption / audit ----------

    /** Applied filters in words (names, not ids) for the screen, the export header and the audit row. */
    private String caption(AccountsReport r, AccountsReportFilter f, AccountsReportSource source) {
        List<String> parts = new ArrayList<>();
        if (r.accepts(AccountsReportParam.EMP_STATUS) && f.empStatus() != null) {
            parts.add("Status: " + switch (f.empStatus()) {
                case ACTIVE -> "Active";
                case NON_ACTIVE -> "Non-Active";
                case ALL -> "All";
            });
        }
        if (r.accepts(AccountsReportParam.MONTH) && f.month() != null) parts.add("Month: " + MONTH.format(f.month()));
        if (r.accepts(AccountsReportParam.DATE_RANGE) && (f.dateFrom() != null || f.dateTo() != null)) {
            parts.add("Period: " + date(f.dateFrom()) + " to " + date(f.dateTo()));
        }
        if (r.accepts(AccountsReportParam.AS_ON_DATE) && f.asOnDate() != null) parts.add("As on: " + date(f.asOnDate()));
        if (r.accepts(AccountsReportParam.BALANCE) && f.balance() != null) {
            parts.add("Balance: " + switch (f.balance()) {
                case GREATER_THAN_ZERO -> "> 0";
                case EQUAL_ZERO -> "= 0";
            });
        }
        if (r.accepts(AccountsReportParam.FISCAL_YEAR) && f.fiscalYearCode() != null) {
            parts.add("Financial Period: " + f.fiscalYearCode());
        }
        if (r.accepts(AccountsReportParam.BONUS_NO) && f.bonusNo() != null) parts.add("Bonus No: " + f.bonusNo());
        if (r.accepts(AccountsReportParam.CARD_RANGE) && (f.cardFrom() != null || f.cardTo() != null)) {
            parts.add("Cards: " + (f.cardFrom() == null ? "" : f.cardFrom()) + " - "
                    + (f.cardTo() == null ? "" : f.cardTo()));
        }
        if (r.accepts(AccountsReportParam.CARD_LIST) && f.cardList() != null && !f.cardList().isEmpty()) {
            parts.add("Card No: " + String.join(", ", f.cardList()));
        }
        boolean org = (r.accepts(AccountsReportParam.DEPARTMENT) && f.departmentId() != null)
                || (r.accepts(AccountsReportParam.SECTION) && f.sectionId() != null)
                || (r.accepts(AccountsReportParam.DESIGNATION) && f.designationId() != null);
        if (org) {
            CatalogCache.CatalogIndex idx = catalog.snapshot();
            if (r.accepts(AccountsReportParam.DEPARTMENT) && f.departmentId() != null) {
                DepartmentDto d = idx.departments().get(f.departmentId());
                parts.add("Department: " + (d == null ? "#" + f.departmentId() : d.name()));
            }
            if (r.accepts(AccountsReportParam.SECTION) && f.sectionId() != null) {
                SectionDto s = idx.sections().get(f.sectionId());
                parts.add("Section: " + (s == null ? "#" + f.sectionId() : s.name()));
            }
            if (r.accepts(AccountsReportParam.DESIGNATION) && f.designationId() != null) {
                DesignationDto d = idx.designations().get(f.designationId());
                parts.add("Designation: " + (d == null ? "#" + f.designationId() : d.title()));
            }
        }
        if (r.accepts(AccountsReportParam.PAYMENT_MODE) && f.paymentMode() != null) {
            parts.add("Payment Mode: " + switch (f.paymentMode()) {
                case ALL -> "All";
                case CASH -> "Cash";
                case BANK_TRANSFER -> "Bank Transfer";
            });
        }
        if (r.accepts(AccountsReportParam.EMPLOYEE_TYPE) && f.employeeType() != null) {
            parts.add("Employee Type: " + switch (f.employeeType()) {
                case ALL -> "All";
                case PRODUCTION -> "Production";
                case NON_PRODUCTION -> "Non-Production";
            });
        }
        if (r.accepts(AccountsReportParam.GRADE_TYPE) && f.gradeType() != null) {
            parts.add("Grade Type: " + switch (f.gradeType()) {
                case MANAGEMENT -> "Management";
                case SUPERVISOR -> "Supervisor";
                case LABOUR -> "Labour";
            });
        }
        parts.addAll(source.captionParts(r, f));
        if (f.search() != null && !f.search().isBlank()) parts.add("Search: " + f.search().trim());
        return String.join("   |   ", parts);
    }

    private void emitAudit(Computed c, String format) {
        audit.emit("report", "0", "accounts." + c.report().id() + ".run", null,
                Map.of("title", c.report().title(), "filters", c.caption(), "format", format,
                        "rowCount", c.rows().size()),
                false);
    }

    // ---------- export ----------

    private byte[] xlsx(Computed c) {
        AccountsReport r = c.report();
        List<AccountsReportColumnDto> cols = c.shownColumns();
        String[] labels = cols.stream().map(AccountsReportColumnDto::label).toArray(String[]::new);
        int[] widths = cols.stream().mapToInt(AccountsReportService::width).toArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XlsxReport wb = new XlsxReport(r.id())) {
            wb.header(SalaryCertificateService.COMPANY_NAME, r.title(), fullCaption(c), generated(c), labels, widths);
            forEachRow(c, row -> wb.row(cols.stream().map(col -> cell(col, row.get(col.key()))).toArray()),
                    g -> wb.totalRow(subtotalCells(g, cols)));
            wb.totalRow(totalCells(c, cols));
            wb.write(out);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    private byte[] csv(Computed c) {
        List<AccountsReportColumnDto> cols = c.shownColumns();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            CsvWriter csv = new CsvWriter(w);
            csv.writeBom();
            csv.writeRow(cols.stream().map(AccountsReportColumnDto::label).toList());
            IoConsumer<List<?>> write = csv::writeRow;
            forEachRow(c, row -> write.accept(cols.stream().map(col -> csvCell(col, row.get(col.key()))).toList()),
                    g -> write.accept(plain(subtotalCells(g, cols))));
            csv.writeRow(plain(totalCells(c, cols)));
            csv.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    @FunctionalInterface
    private interface IoConsumer<T> {
        void accept(T t) throws IOException;
    }

    /** Rows in print order; for grouped reports a subtotal after each group's last row. */
    private static void forEachRow(Computed c, IoConsumer<Map<String, Object>> row,
                                   IoConsumer<Map<String, Object>> subtotal) throws IOException {
        if (c.groups() == null) {
            for (Map<String, Object> r : c.rows()) row.accept(r);
            return;
        }
        int i = 0;
        for (Map<String, Object> g : c.groups()) {
            int count = (Integer) g.get("rows");
            for (int k = 0; k < count; k++) row.accept(c.rows().get(i++));
            subtotal.accept(g);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object[] subtotalCells(Map<String, Object> group, List<AccountsReportColumnDto> cols) {
        Object[] cells = new Object[cols.size()];
        cells[0] = "Subtotal";
        Map<String, BigDecimal> totals = (Map<String, BigDecimal>) group.get("totals");
        if (cols.size() > 1 && !totals.containsKey(cols.get(1).key())) {
            cells[1] = nz(group.get("group")) + " - " + group.get("employees") + " employee(s)";
        }
        for (int i = 0; i < cols.size(); i++) {
            BigDecimal t = totals.get(cols.get(i).key());
            if (t != null) cells[i] = t;
        }
        return cells;
    }

    private static List<Object> plain(Object[] cells) {
        return Arrays.stream(cells).map(v -> v instanceof BigDecimal b ? (Object) b.toPlainString() : v).toList();
    }

    /** "Total" + employee count, then each totalable column's grand total in its own cell. */
    private static Object[] totalCells(Computed c, List<AccountsReportColumnDto> cols) {
        Object[] cells = new Object[cols.size()];
        cells[0] = c.report().output() == AccountsReportOutput.LEDGER ? "Closing" : "Total";
        if (cols.size() > 1 && !c.totals().containsKey(cols.get(1).key())) {
            cells[1] = c.totalEmployees() + " employee(s)";
        }
        for (int i = 0; i < cols.size(); i++) {
            BigDecimal t = c.totals().get(cols.get(i).key());
            if (t != null) cells[i] = t;
        }
        return cells;
    }

    private static String fullCaption(Computed c) {
        if (c.header() == null) return c.caption();
        Map<String, Object> h = c.header();
        String who = "Card: " + h.get(AccountsReportColumnDto.CARD_NO)
                + "   |   Name: " + nz(h.get(AccountsReportColumnDto.EMPLOYEE_NAME))
                + "   |   Father: " + nz(h.get(AccountsReportColumnDto.FATHER_NAME))
                + "   |   CNIC: " + nz(h.get(AccountsReportColumnDto.CNIC))
                + "   |   DOJ: " + date((LocalDate) h.get(AccountsReportColumnDto.DATE_OF_JOINING))
                + "   |   Department: " + nz(h.get(AccountsReportColumnDto.DEPARTMENT))
                + "   |   Designation: " + nz(h.get(AccountsReportColumnDto.DESIGNATION));
        return c.caption().isBlank() ? who : who + "   |   " + c.caption();
    }

    private static Object cell(AccountsReportColumnDto col, Object v) {
        if (v == null) return null;
        if (col.type() == ReportColumnType.DATE && v instanceof LocalDate d) return DATE.format(d);
        if (col.type() == ReportColumnType.MONTH && v instanceof YearMonth m) return MONTH.format(m);
        return v;
    }

    private static Object csvCell(AccountsReportColumnDto col, Object v) {
        Object c = cell(col, v);
        return c instanceof BigDecimal b ? b.toPlainString() : c;
    }

    private static int width(AccountsReportColumnDto col) {
        if (col.key().equals(AccountsReportColumnDto.SR_NO)) return 6;
        if (col.key().equals(AccountsReportColumnDto.EMPLOYEE_NAME) || col.key().equals("description")) return 28;
        return switch (col.type()) {
            case AMOUNT -> 15;
            case DATE -> 12;
            case MONTH -> 16;
            case INT, DECIMAL -> 10;
            case TEXT -> 20;
        };
    }

    private static String generated(Computed c) {
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        return "Printed " + STAMP.format(LocalDateTime.now()) + " by "
                + (actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username())
                + "   |   Total employees: " + c.totalEmployees();
    }

    private static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    private static String nz(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String slug(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static AccountsReport require(String reportId) {
        return AccountsReport.byId(reportId).orElseThrow(() -> notFound(reportId));
    }

    private static NotFoundError notFound(String reportId) {
        return new NotFoundError(PayrollErrorCode.ACCOUNTS_REPORT_NOT_FOUND, "no accounts report " + reportId);
    }
}
