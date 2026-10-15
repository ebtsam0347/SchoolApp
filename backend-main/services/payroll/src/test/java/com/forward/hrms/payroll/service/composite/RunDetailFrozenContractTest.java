package com.forward.hrms.payroll.service.composite;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.client.EmployeeClient.EmployeeBrief;
import com.forward.hrms.payroll.client.dto.attendace.AnnualLeaveSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.EmployeeLeavePayslipSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.LeaveTypeAvailedDto;
import com.forward.hrms.payroll.client.dto.attendace.PayrollAttendanceAuditDto;
import com.forward.hrms.payroll.controller.composite.PayrollCompositesController;
import com.forward.hrms.payroll.dto.response.EmployeeBankDetailsDto;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;
import com.forward.hrms.payroll.dto.response.PayrollRunTotalsDto;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.SalaryComponentDto;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
import com.forward.hrms.payroll.repository.projection.EmployeeAppliedAdvanceProjection;
import com.forward.hrms.payroll.repository.projection.PayslipProvenanceProjection;
import com.forward.hrms.payroll.service.BonusEventService;
import com.forward.hrms.payroll.service.EmployeeBankDetailsService;
import com.forward.hrms.payroll.service.EmployeePayrollSettingsService;
import com.forward.hrms.payroll.service.EmployeeSalaryComponentService;
import com.forward.hrms.payroll.service.EmployeeSalaryService;
import com.forward.hrms.payroll.service.GratuityService;
import com.forward.hrms.payroll.service.IncrementBatchService;
import com.forward.hrms.payroll.service.OffCyclePaymentService;
import com.forward.hrms.payroll.service.PayrollRunService;
import com.forward.hrms.payroll.service.PayslipService;
import com.forward.hrms.payroll.service.PermanentAdvanceService;
import com.forward.hrms.payroll.service.SalaryComponentService;
import com.forward.hrms.payroll.service.composite.RunDetailFixtures.Fixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Freezes the EXISTING {@code GET /payroll/runs/{id}/detail} response contract and proves the additive
 * {@code salarySlip} / {@code supplementary} objects break none of it.
 *
 * <p>Three frozen goldens (test resources, captured BEFORE the change):
 * <ul>
 *   <li>{@code run-detail-frozen-records.txt}: every response record's components (name + type + order),
 *       read by reflection from the pre-change build;</li>
 *   <li>{@code run-detail-frozen-wire-paths.json}: every JSON path and JSON type observed on the live
 *       pre-change API for Runs 1, 2 and 3 (3,968 + 3,816 + 37 payslips);</li>
 *   <li>{@code run-detail-frozen-controller.txt} / {@code -enums.txt}: endpoint URLs and enum values.</li>
 * </ul>
 * The JSON is produced by the real {@link PayrollCompositesService} pipeline (only its collaborators are
 * mocked) and the real controller through Spring MVC's message converter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RunDetailFrozenContractTest {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @Mock PayslipService payslips;
    @Mock PayrollRunService runs;
    @Mock IncrementBatchService incrementBatches;
    @Mock BonusEventService bonusEvents;
    @Mock OffCyclePaymentService offCyclePayments;
    @Mock EmployeeSalaryService salaries;
    @Mock EmployeeSalaryComponentService salaryComponents;
    @Mock SalaryComponentService components;
    @Mock EmployeePayrollSettingsService settings;
    @Mock EmployeeBankDetailsService bankDetails;
    @Mock GratuityService gratuity;
    @Mock EmployeeClient employees;
    @Mock AttendanceClient attendance;
    @Mock PermanentAdvanceService permanentAdvances;
    @Mock PayslipLineRepository payslipLines;
    @Mock PayslipRepository payslipRepository;
    @Mock ShortTermAdvanceRepository shortTermAdvances;
    @Mock PermanentAdvanceRepository permanentAdvanceRepository;
    @Mock PermanentAdvanceDeductionRepository permanentAdvanceDeductions;
    @Mock EmployeeDisplayCache displayCache;

    @InjectMocks PayrollCompositesService service;

    private MockMvc mvc;

    // advances test data read by the stubs in detailJson(...)
    private List<ShortTermAdvance> shortTermRows = List.of();
    private Map<Long, BigDecimal> permanentAppliedByEmployee = Map.of();
    private Map<Long, BigDecimal> ledgerByEmployee = Map.of();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PayrollCompositesController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    // ================================================================= 1. record structure

    private static final String DTO_PKG = "com.forward.hrms.payroll.dto.response.composite.PayrollResponses$";

    private static Class<?> recordClass(String simpleName) throws ClassNotFoundException {
        return switch (simpleName) {
            case "PayrollRunDto" -> PayrollRunDto.class;
            case "PageResponse" -> PageResponse.class;
            default -> Class.forName(DTO_PKG + simpleName);
        };
    }

    private static String describe(Class<?> c) {
        return Arrays.stream(c.getRecordComponents())
                .map(rc -> rc.getName() + ":" + rc.getGenericType().getTypeName().replace(DTO_PKG, ""))
                .collect(Collectors.joining(", "));
    }

    @Test
    void existingResponseRecords_keepEveryComponent_sameNameTypeAndOrder() throws Exception {
        List<String> frozen = readLines("contract/run-detail-frozen-records.txt");
        assertThat(frozen).hasSize(18);

        for (String line : frozen) {
            String simple = line.substring(0, line.indexOf(" | "));
            List<String> frozenComponents = List.of(line.substring(line.indexOf(" | ") + 3).split(", (?![^<]*>)"));
            Class<?> c = recordClass(simple);
            List<String> actual = Arrays.stream(c.getRecordComponents())
                    .map(rc -> rc.getName() + ":" + rc.getGenericType().getTypeName().replace(DTO_PKG, ""))
                    .toList();

            assertThat(actual).as(simple + " must match the agreed contract").isEqualTo(frozenComponents);
        }
    }

    @Test
    void existingEnumValues_areUnchanged() {
        Map<String, String> frozen = readLines("contract/run-detail-frozen-enums.txt").stream()
                .collect(Collectors.toMap(l -> l.substring(0, l.indexOf(':')), l -> l.substring(l.indexOf(':') + 2)));
        assertThat(Arrays.stream(PayslipStatus.values()).map(Enum::name).collect(Collectors.joining(", ")))
                .isEqualTo(frozen.get("PayslipStatus"));
        assertThat(Arrays.stream(PayrollRunStatus.values()).map(Enum::name).collect(Collectors.joining(", ")))
                .isEqualTo(frozen.get("PayrollRunStatus"));
    }

    // ================================================================= 2. endpoint URLs

    @Test
    void controllerUrlsAndHttpMethods_areUnchanged() {
        Set<String> actual = new TreeSet<>();
        for (Method m : PayrollCompositesController.class.getDeclaredMethods()) {
            GetMapping gm = m.getAnnotation(GetMapping.class);
            if (gm != null) for (String path : gm.value()) actual.add("GET " + path);
        }
        assertThat(actual).isEqualTo(new TreeSet<>(readLines("contract/run-detail-frozen-controller.txt")));
    }

    // ================================================================= 3. wire contract per scenario

    @Test
    void run1_paidAndHeld_legacy_keepEveryFrozenPath() throws Exception {
        assertFrozenContract(detailJson(1, PayrollRunStatus.POSTED,
                RunDetailFixtures.run1Paid10005(), RunDetailFixtures.run1Held131()));
    }

    @Test
    void run2_restoredLegacy_keepEveryFrozenPath() throws Exception {
        assertFrozenContract(detailJson(2, PayrollRunStatus.POSTED,
                RunDetailFixtures.run2Paid13411(), RunDetailFixtures.run2Held10839(),
                RunDetailFixtures.run2Held528(), RunDetailFixtures.run2Paid368(),
                RunDetailFixtures.run2Paid1258(), RunDetailFixtures.run2PaidOvertime12680(),
                RunDetailFixtures.run2PaidNoEobiNoTax()));
    }

    @Test
    void run3_heldLegacy_keepsEveryFrozenPath() throws Exception {
        assertFrozenContract(detailJson(3, PayrollRunStatus.POSTED, RunDetailFixtures.run3Held4151()));
    }

    @Test
    void currentPayrollEngine_keepsEveryFrozenPath() throws Exception {
        assertFrozenContract(detailJson(2, PayrollRunStatus.COMPUTED, RunDetailFixtures.enginePaid()));
    }

    @Test
    void zeroValueComponent_keepsEveryFrozenPath() throws Exception {
        assertFrozenContract(detailJson(2, PayrollRunStatus.COMPUTED, RunDetailFixtures.engineZeroValues()));
    }

    @Test
    void everyPayslipStatus_keepsEveryFrozenPath() throws Exception {
        assertFrozenContract(detailJson(2, PayrollRunStatus.POSTED,
                RunDetailFixtures.engineWithStatus(PayslipStatus.RELEASE_PENDING),
                RunDetailFixtures.engineWithStatus(PayslipStatus.RELEASED),
                RunDetailFixtures.engineWithStatus(PayslipStatus.HELD),
                RunDetailFixtures.engineWithStatus(PayslipStatus.DRAFT)));
    }

    /** Best-effort lookups failing (no bank, no attendance) must not drop or reshape any other field. */
    @Test
    void whenLiveLookupsAreEmpty_frozenPathsStillPresent_andAttendanceIsNull() throws Exception {
        Fixture f = RunDetailFixtures.run2Paid13411();
        JsonNode bare = detailJson(2, PayrollRunStatus.POSTED, List.of(f), false);

        JsonNode row = bare.get("payslips").get("content").get(0);
        assertThat(row.get("bank").isNull()).isTrue();
        assertThat(row.get("leaves").isNull()).isTrue();
        assertThat(row.get("attendance").isNull()).isTrue();
        assertFrozenContract(bare);
    }

    @Test
    void row_hasExactlyTheAgreedTopLevelProperties_andAttendanceMatchesTheDerivedFigures() throws Exception {
        Fixture f = RunDetailFixtures.run2Paid13411();
        JsonNode row = detailJson(2, PayrollRunStatus.POSTED, List.of(f), true).get("payslips").get("content").get(0);

        // earnings/deductions blocks come from the same static mappers as before, byte for byte
        var expectedEarnings = PayrollCompositesService.legacyEarningLines(f.payslip(), catalog(ComponentType.EARNING), f.lines());
        var expectedDeductions = PayrollCompositesService.withAdvanceLines(
                PayrollCompositesService.legacyDeductionLines(f.payslip(), catalog(ComponentType.DEDUCTION), f.lines()),
                new PayrollResponses.PayslipAdvances(
                        new PayrollResponses.PayslipAdvanceInfo(BigDecimal.ZERO, BigDecimal.ZERO),
                        new PayrollResponses.PayslipAdvanceInfo(BigDecimal.ZERO, BigDecimal.ZERO)));
        assertThat(row.get("earnings").get("lines").toString()).isEqualTo(JSON.valueToTree(expectedEarnings).toString());
        assertThat(row.get("deductions").get("lines").toString()).isEqualTo(JSON.valueToTree(expectedDeductions).toString());
        assertThat(row.get("salary").get("grossSalary").decimalValue()).isEqualByComparingTo("58300");
        assertThat(row.get("salary").get("netSalary").decimalValue()).isEqualByComparingTo("20709");
        assertThat(row.get("salary").get("totalDeduction").decimalValue()).isEqualByComparingTo("37591");

        // no salarySlip / supplementary, and no attendance.audit
        List<String> names = new ArrayList<>();
        row.propertyNames().forEach(names::add);
        assertThat(names).containsExactly("id", "periodYear", "periodMonth", "fiscalYearCode", "status", "heldReason",
                "generatedAt", "employee", "bank", "salary", "earnings", "deductions", "attendance", "leaves",
                "reconciliation", "advances");

        // attendance = the derived figures, in the attendanceAudit order, without preJoiningDays
        JsonNode att = row.get("attendance");
        List<String> attNames = new ArrayList<>();
        att.propertyNames().forEach(attNames::add);
        assertThat(attNames).containsExactly("weeklyOffDays", "holidayDays", "workingDays", "presentDays",
                "paidLeaveDays", "unpaidLeaveDays", "absentDays", "unrecordedAbsentDays", "halfDayRecords",
                "presentOnOffDays", "leaveConflictDays", "lopDays", "paidDays");
        assertThat(att.get("weeklyOffDays").asInt()).isEqualTo(5);
        assertThat(att.get("holidayDays").asInt()).isEqualTo(2);
        assertThat(att.get("workingDays").asInt()).isEqualTo(26);
        assertThat(att.get("presentDays").decimalValue()).isEqualByComparingTo("20");
        assertThat(att.get("paidDays").decimalValue()).isEqualByComparingTo("25.5");
    }

    @Test
    void runAndSummaryBlocks_areUnchanged_andTiedToTheTotalsSource() throws Exception {
        JsonNode body = detailJson(2, PayrollRunStatus.POSTED, List.of(RunDetailFixtures.run2Paid13411()), true);
        assertThat(body.get("summary").get("gross").decimalValue()).isEqualByComparingTo("197797387");
        assertThat(body.get("summary").get("allowances").decimalValue()).isEqualByComparingTo("201786740");
        assertThat(body.get("summary").get("deductions").decimalValue()).isEqualByComparingTo("69267522");
        assertThat(body.get("summary").get("net").decimalValue()).isEqualByComparingTo("130002373");
        assertThat(body.get("summary").get("employees").asInt()).isEqualTo(3997);
        assertThat(body.get("run").get("status").asString()).isEqualTo("POSTED");
    }

    // ================================================================= 4. the guard itself catches breakage

    @Test
    void guard_failsWhenAnExistingFieldIsRemoved_renamed_orRetyped() throws Exception {
        JsonNode ok = detailJson(2, PayrollRunStatus.POSTED, List.of(RunDetailFixtures.run2Paid13411()), true);
        assertFrozenContract(ok); // baseline passes

        JsonNode removed = ok.deepCopy();
        ((ObjectNode) removed.get("payslips").get("content").get(0).get("salary")).remove("netSalary");
        assertThat(violation(removed)).contains("MISSING payslips.content[].salary.netSalary");

        JsonNode renamed = ok.deepCopy();
        ObjectNode earnings = (ObjectNode) renamed.get("payslips").get("content").get(0).get("earnings");
        JsonNode gross = earnings.remove("grossSalary");
        earnings.set("gross", gross);
        assertThat(violation(renamed)).contains("MISSING payslips.content[].earnings.grossSalary");

        JsonNode retyped = ok.deepCopy();
        ((ObjectNode) retyped.get("payslips").get("content").get(0).get("salary")).put("basic", "58300");
        assertThat(violation(retyped)).contains("TYPE payslips.content[].salary.basic");

        JsonNode moved = ok.deepCopy();
        ObjectNode row = (ObjectNode) moved.get("payslips").get("content").get(0);
        row.set("payment", row.remove("bank"));
        assertThat(violation(moved)).contains("MISSING payslips.content[].bank");
    }

    private String violation(JsonNode response) {
        try {
            assertFrozenContract(response);
            return "";
        } catch (AssertionError | IOException e) {
            return String.valueOf(e.getMessage());
        }
    }

    // ================================================================= 5. advances block

    @Test
    void advances_legacyPayslip_showsAppliedAndDeducted_withoutTouchingTheNet() throws Exception {
        Fixture f = RunDetailFixtures.run2Paid13411();          // persisted deductions 37,108, net 20,709
        long emp = f.payslip().employeeId();
        shortTermRows = List.of(shortTerm(emp, "30000.00", ShortTermAdvanceStatus.APPROVED, null));
        permanentAppliedByEmployee = Map.of(emp, d("50000.00"));
        ledgerByEmployee = Map.of(emp, d("3000.00"));

        JsonNode row = detailJson(2, PayrollRunStatus.POSTED, List.of(f), true).get("payslips").get("content").get(0);

        JsonNode adv = row.get("advances");
        assertThat(adv.get("shortTermAdvance").get("appliedAmount").decimalValue()).isEqualByComparingTo("30000");
        assertThat(adv.get("shortTermAdvance").get("deductedAmount").decimalValue()).isEqualByComparingTo("30000");
        assertThat(adv.get("permanentAdvance").get("appliedAmount").decimalValue()).isEqualByComparingTo("50000");
        assertThat(adv.get("permanentAdvance").get("deductedAmount").decimalValue()).isEqualByComparingTo("3000");

        // both advances also appear in deductions.lines
        Map<String, String> byCode = new java.util.LinkedHashMap<>();
        row.get("deductions").get("lines").forEach(n -> byCode.put(n.get("code").asString(), n.get("amount").toString()));
        assertThat(byCode).containsEntry("PERMANENT_ADVANCE", "3000.00").containsEntry("SHORT_TERM_ADVANCE", "30000.00");

        // and nothing about the net changed: persisted totals only
        assertThat(row.get("salary").get("totalDeduction").decimalValue()).isEqualByComparingTo("37591");
        assertThat(row.get("salary").get("netSalary").decimalValue()).isEqualByComparingTo("20709");
        assertThat(row.get("deductions").get("totalDeduction").decimalValue()).isEqualByComparingTo("37591");
        assertThat(row.get("earnings").get("grossSalary").decimalValue()).isEqualByComparingTo("58300");
    }

    @Test
    void advances_enginePayslip_appliedShown_butNothingDeducted_soNetStaysConsistent() throws Exception {
        Fixture f = RunDetailFixtures.enginePaid();
        long emp = f.payslip().employeeId();
        shortTermRows = List.of(shortTerm(emp, "10000.00", ShortTermAdvanceStatus.APPROVED, null));
        permanentAppliedByEmployee = Map.of(emp, d("20000.00"));
        ledgerByEmployee = Map.of(emp, d("2000.00"));      // ledger row exists, but the engine net never deducted it

        JsonNode row = detailJson(2, PayrollRunStatus.COMPUTED, List.of(f), true).get("payslips").get("content").get(0);

        JsonNode adv = row.get("advances");
        assertThat(adv.get("shortTermAdvance").get("appliedAmount").decimalValue()).isEqualByComparingTo("10000");
        assertThat(adv.get("shortTermAdvance").get("deductedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(adv.get("permanentAdvance").get("appliedAmount").decimalValue()).isEqualByComparingTo("20000");
        assertThat(adv.get("permanentAdvance").get("deductedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(row.get("salary").get("netSalary").decimalValue()).isEqualByComparingTo("40600");
        assertThat(row.get("reconciliation").get("reconciled").asBoolean()).isTrue();
    }

    @Test
    void advances_noAdvances_areExplicitZeros() throws Exception {
        JsonNode row = detailJson(2, PayrollRunStatus.POSTED, List.of(RunDetailFixtures.run2Paid368()), true)
                .get("payslips").get("content").get(0);
        for (String kind : List.of("permanentAdvance", "shortTermAdvance")) {
            assertThat(row.get("advances").get(kind).get("appliedAmount").decimalValue()).isEqualByComparingTo("0");
            assertThat(row.get("advances").get(kind).get("deductedAmount").decimalValue()).isEqualByComparingTo("0");
        }
    }

    @Test
    void advances_deductedIsCappedAtWhatThePersistedDeductionsCanHold() {
        Fixture f = RunDetailFixtures.run2Paid368();             // persisted deductions 8,000
        var sta = List.of(shortTerm(f.payslip().employeeId(), "8000.00", ShortTermAdvanceStatus.APPROVED, null));

        var adv = PayrollCompositesService.advances(f.payslip(), true, sta, d("0"), d("5000.00"), f.lines());

        assertThat(adv.shortTermAdvance().deductedAmount()).isEqualByComparingTo("8000");
        assertThat(adv.permanentAdvance().deductedAmount()).isEqualByComparingTo("0");   // no room left, never over-shown
    }

    @Test
    void advances_rejectedLines_areAppliedButNotDeducted_forLegacy() {
        Fixture f = RunDetailFixtures.run2Paid13411();
        long emp = f.payslip().employeeId();
        var approved = shortTerm(emp, "1000.00", ShortTermAdvanceStatus.APPROVED, null);
        var rejected = shortTerm(emp, "2000.00", ShortTermAdvanceStatus.REJECTED, null);

        var adv = PayrollCompositesService.advances(f.payslip(), true, List.of(approved, rejected), d("0"), d("0"), f.lines());

        assertThat(adv.shortTermAdvance().appliedAmount()).isEqualByComparingTo("3000");
        assertThat(adv.shortTermAdvance().deductedAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void advanceLines_areNotDuplicated_whenThePayslipAlreadyCarriesThem() {
        var existing = List.of(
                new PayrollResponses.PayslipLineItem("PERMANENT_ADVANCE", "Permanent Advance", d("700")),
                new PayrollResponses.PayslipLineItem("EOBI", "EOBI", d("400")));
        var adv = new PayrollResponses.PayslipAdvances(
                new PayrollResponses.PayslipAdvanceInfo(d("0"), d("999")),
                new PayrollResponses.PayslipAdvanceInfo(d("5"), d("5")));

        var lines = PayrollCompositesService.withAdvanceLines(existing, adv);

        assertThat(lines).extracting(PayrollResponses.PayslipLineItem::code)
                .containsExactly("PERMANENT_ADVANCE", "EOBI", "SHORT_TERM_ADVANCE");
        assertThat(lines.get(0).amount()).isEqualByComparingTo("700");   // the existing line is kept untouched
    }

    // ================================================================= helpers

    private static List<SalaryComponentDto> catalog(ComponentType type) {
        return type == ComponentType.EARNING
                ? List.of(c("HRA", "House Rent Allowance", type, 10), c("MEAL", "Meal Allowance", type, 40),
                        c("EXTRA", "Extra Allowance", type, 44), c("SENIORITY", "Seniority Allowance", type, 46))
                : List.of(c("INCOME_TAX", "Income Tax", type, 90), c("RENT", "Rent Deduction", type, 92),
                        c("CANTEEN", "Canteen Deduction", type, 93), c("LOAN", "Loan Repayment", type, 95),
                        c("ADVANCE", "Salary Advance", type, 96), c("OTHER", "Other", type, 99));
    }

    private static SalaryComponentDto c(String code, String name, ComponentType type, int order) {
        return new SalaryComponentDto(0L, code, name, type, CalcType.FLAT, null, order, true, false);
    }

    private JsonNode detailJson(long runId, PayrollRunStatus runStatus, Fixture... fixtures) throws Exception {
        return detailJson(runId, runStatus, List.of(fixtures), true);
    }

    private JsonNode detailJson(long runId, PayrollRunStatus runStatus, List<Fixture> fixtures, boolean liveData)
            throws Exception {
        List<PayslipDto> slice = fixtures.stream().map(Fixture::payslip).toList();
        List<PayslipLine> lines = fixtures.stream().flatMap(f -> f.lines().stream()).toList();

        when(runs.get(runId)).thenReturn(new PayrollRunDto(runId, "2026-27", 2026, 8, runStatus, true,
                null, null, LocalDateTime.of(2026, 8, 10, 11, 29, 39), null, null, 0, 0, 0));
        when(runs.totals(eq(runId), any(), any())).thenReturn(new PayrollRunTotalsDto(runId, 3997,
                d("195524952"), d("201786740"), d("197797387"), d("69267522"), d("1862863"), d("1541596"),
                d("8272114"), d("130002373"), 141, 0));
        when(runs.payslips(eq(runId), any(), any(), any())).thenReturn(
                new PageResponse<>(slice, 0, 20, slice.size(), 1, "id: ASC"));

        Map<Long, EmployeeDisplay> displays = new LinkedHashMap<>();
        Map<Long, EmployeeBrief> briefs = new LinkedHashMap<>();
        Map<Long, EmployeeBankDetailsDto> banks = new LinkedHashMap<>();
        Map<Long, EmployeeLeavePayslipSummaryDto> att = new LinkedHashMap<>();
        for (PayslipDto p : slice) {
            displays.put(p.employeeId(), new EmployeeDisplay(p.employeeId(), p.employeeCode(), "Name " + p.employeeCode(),
                    1L, "Dept", 1L, "Section", 1L, "Designation", null));
            briefs.put(p.employeeId(), new EmployeeBrief(p.employeeId(), p.employeeCode(), "Name " + p.employeeCode(),
                    null, null, null, 4L, 1L, null, LocalDate.of(2022, 5, 19), "Father " + p.employeeCode()));
            if (liveData) {
                banks.put(p.employeeId(), new EmployeeBankDetailsDto(1L, p.employeeId(), 1L, "FSL", "Faysal Bank",
                        "Main", "Title", "PK00", "3706391000005070"));
                att.put(p.employeeId(), new EmployeeLeavePayslipSummaryDto(p.employeeId(),
                        List.of(new LeaveTypeAvailedDto("CASUAL", "Casual leave", d("1.5"), d("1.5"))),
                        new AnnualLeaveSummaryDto(d("14"), d("2"), d("0"), d("12")),
                        new PayrollAttendanceAuditDto(31, 0, 5, 2, 26, d("20"), d("1.5"), d("0"), d("3"),
                                d("1"), 0, 0, 0, d("0"), d("25.5"))));
            }
        }
        when(displayCache.getMany(anyCollection())).thenReturn(displays);
        when(employees.getMany(anyCollection(), isNull())).thenReturn(briefs);
        when(employees.getAllGrades(isNull())).thenReturn(List.of(new GradeDto(4L, "A", "A", 1, null, true, 1,
                GradeType.LABOUR)));
        when(bankDetails.getMany(anyCollection())).thenReturn(banks);
        when(permanentAdvances.outstandingBalances(anyCollection())).thenReturn(
                liveData ? Map.of(slice.get(0).employeeId(), d("150000.00")) : Map.of());
        when(components.list(ComponentType.EARNING, true)).thenReturn(catalog(ComponentType.EARNING));
        when(components.list(ComponentType.DEDUCTION, true)).thenReturn(catalog(ComponentType.DEDUCTION));
        when(payslipLines.findAllByPayslipIdInOrderByPayslipIdAscDisplayOrderAsc(anyCollection())).thenReturn(lines);
        when(attendance.payslipSummaries(anyCollection(), any(), any(), isNull())).thenReturn(att);
        when(payslipRepository.findProvenanceByIdIn(anyCollection())).thenReturn(fixtures.stream()
                .map(f -> (PayslipProvenanceProjection) new PayslipProvenanceProjection() {
                    public Long getId() { return f.payslip().id(); }
                    public String getCreatedBy() { return f.createdBy(); }
                }).toList());
        when(shortTermAdvances.findAppliedForEmployeesInMonth(anyCollection(), anyInt(), anyInt()))
                .thenReturn(shortTermRows);
        when(permanentAdvanceRepository.appliedTotalsByEmployeeIds(anyCollection(), any()))
                .thenReturn(projections(permanentAppliedByEmployee));
        when(permanentAdvanceDeductions.deductedInRunByEmployeeIds(anyCollection(), anyLong()))
                .thenReturn(projections(ledgerByEmployee));

        String body = mvc.perform(get("/payroll/runs/{id}/detail", runId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).as("HTTP 200 JSON body").startsWith("{");
        return JSON.readTree(body);
    }

    private static List<EmployeeAppliedAdvanceProjection> projections(Map<Long, BigDecimal> byEmployee) {
        return byEmployee.entrySet().stream()
                .map(e -> (EmployeeAppliedAdvanceProjection) new EmployeeAppliedAdvanceProjection() {
                    public Long getEmployeeId() { return e.getKey(); }
                    public BigDecimal getAppliedAmount() { return e.getValue(); }
                }).toList();
    }

    private static ShortTermAdvance shortTerm(long employeeId, String amount, ShortTermAdvanceStatus status,
                                              Long deductedInRunId) {
        ShortTermAdvance a = new ShortTermAdvance();
        a.setEmployeeId(employeeId);
        a.setAmount(new BigDecimal(amount));
        a.setTargetYear(2026);
        a.setTargetMonth(8);
        a.setStatus(status);
        a.setDeductedInRunId(deductedInRunId);
        return a;
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    // ---------------------------------------------------------------- frozen path check

    private static String type(JsonNode n) {
        return switch (n.getNodeType()) {
            case NULL, MISSING -> "null";
            case ARRAY -> "array";
            case OBJECT -> "object";
            case NUMBER -> "number";
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            default -> "other";
        };
    }

    private static void collect(JsonNode n, String path, Map<String, Set<String>> types, Set<String> emptyContainers) {
        String t = type(n);
        types.computeIfAbsent(path, k -> new TreeSet<>()).add(t);
        if (t.equals("null")) emptyContainers.add(path);
        if (t.equals("array")) {
            if (n.size() == 0) emptyContainers.add(path);
            n.forEach(x -> collect(x, path + "[]", types, emptyContainers));
        } else if (t.equals("object")) {
            n.properties().forEach(e -> collect(e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey(),
                    types, emptyContainers));
        }
    }

    /**
     * Every path of the frozen pre-change wire contract must still be present in the new response with a
     * compatible JSON type. A frozen path may be absent only beneath a container that is null or empty in
     * THIS response (e.g. {@code bank.*} when bank is null), exactly as before.
     */
    private void assertFrozenContract(JsonNode response) throws IOException {
        JsonNode frozen = JSON.readTree(resource("contract/run-detail-frozen-wire-paths.json"));
        Map<String, Set<String>> actual = new TreeMap<>();
        Set<String> emptyContainers = new HashSet<>();
        collect(response, "", actual, emptyContainers);

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (var e : frozen.properties()) {
            String path = e.getKey();
            Set<String> allowed = new TreeSet<>();
            e.getValue().forEach(t -> allowed.add(t.asString()));
            // a path only ever seen as null in the captured data has no observed type: its declared type is
            // frozen by the record-component golden, so any JSON type is acceptable here
            boolean observedOnlyNull = allowed.equals(Set.of("null"));
            allowed.add("null");
            checked++;

            Set<String> got = actual.get(path);
            if (got == null) {
                boolean underEmpty = emptyContainers.stream()
                        .anyMatch(x -> path.startsWith(x + ".") || path.startsWith(x + "[]"));
                if (!underEmpty) problems.add("MISSING " + path);
                continue;
            }
            if (!observedOnlyNull && !allowed.containsAll(got)) problems.add("TYPE " + path + " frozen=" + allowed + " actual=" + got);
        }
        assertThat(problems).as("existing contract violations (%d frozen paths checked)", checked).isEmpty();
        assertThat(checked).isGreaterThan(100);
    }

    private static InputStream resource(String name) {
        InputStream in = RunDetailFrozenContractTest.class.getClassLoader().getResourceAsStream(name);
        assertThat(in).as("test resource " + name).isNotNull();
        return in;
    }

    private static List<String> readLines(String name) {
        try (InputStream in = resource(name)) {
            return Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R"))
                    .filter(s -> !s.isBlank()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
