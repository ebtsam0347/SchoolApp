package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.payroll.dto.request.SetAllowancesRequest;
import com.forward.hrms.payroll.dto.response.AllowanceHistoryEntryDto;
import com.forward.hrms.payroll.dto.response.EmployeeAllowanceRowDto;
import com.forward.hrms.payroll.dto.response.EmployeeAllowancesDto;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeAllowanceServiceTest {

    private static final long EMP = 42L;

    private final EmployeeSalaryComponentRepository lines = mock(EmployeeSalaryComponentRepository.class);
    private final SalaryComponentRepository components = mock(SalaryComponentRepository.class);
    private final EmployeeSalaryComponentService salaryComponents = mock(EmployeeSalaryComponentService.class);
    private final EmployeeDisplayCache displayCache = mock(EmployeeDisplayCache.class);

    private final SalaryComponent travel = component(1L, "TRAVEL", "Travel");
    private final SalaryComponent education = component(2L, "EDUCATION", "Education");
    private final SalaryComponent mobile = component(3L, "MOBILE", "Mobile");
    private final SalaryComponent special = component(4L, "SPECIAL", "Special");

    private EmployeeAllowanceService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeAllowanceService(lines, components, salaryComponents, displayCache);
        when(components.findAllowanceComponents()).thenReturn(List.of(travel, education, mobile, special));
        when(components.findAllById(anyCollection())).thenReturn(List.of(travel, education, mobile, special));
        when(displayCache.get(EMP)).thenReturn(display(EMP, "16", "Muhammad Zeeshan"));
    }

    @Test
    void historyHasOneVersionPerSaveNewestFirst() {
        // 1 Oct 09:00 ayesha sets Travel 4000 + Mobile 1500 (one save, writes ms apart).
        EmployeeSalaryComponent travel1 = line(1L, travel, "4000", "2026-10-01", "2026-10-10");
        opened(travel1, "ayesha", "2026-10-01T09:00:00.100");
        closed(travel1, "bilal", "2026-10-10T11:00:00.200");
        EmployeeSalaryComponent mobile1 = line(2L, mobile, "1500", "2026-10-01", "2026-10-10");
        opened(mobile1, "ayesha", "2026-10-01T09:00:00.150");
        closed(mobile1, "bilal", "2026-10-10T15:00:00.000");
        // 10 Oct 11:00 bilal re-prices Travel to 5000.
        EmployeeSalaryComponent travel2 = line(3L, travel, "5000", "2026-10-10", null);
        opened(travel2, "bilal", "2026-10-10T11:00:00.250");
        // 10 Oct 15:00 bilal removes Mobile (same day, still its own version).
        when(lines.findAllowanceLines(EMP)).thenReturn(List.of(travel1, mobile1, travel2));

        List<AllowanceHistoryEntryDto> versions = service.history(EMP);

        assertThat(versions)
                .extracting(AllowanceHistoryEntryDto::total, AllowanceHistoryEntryDto::createdBy,
                        AllowanceHistoryEntryDto::updatedBy, AllowanceHistoryEntryDto::effectiveTo)
                .containsExactly(
                        tuple(new BigDecimal("5000"), "bilal", null, null),
                        tuple(new BigDecimal("6500"), "bilal", "bilal", LocalDate.parse("2026-10-10")),
                        tuple(new BigDecimal("5500"), "ayesha", "bilal", LocalDate.parse("2026-10-10")));
        assertThat(versions.get(1).amounts())
                .containsEntry("TRAVEL", new BigDecimal("5000"))
                .containsEntry("MOBILE", new BigDecimal("1500"));
        assertThat(versions.get(1).createdAt()).isEqualTo(LocalDateTime.parse("2026-10-10T11:00:00.200"));
        assertThat(versions.get(1).updatedAt()).isEqualTo(LocalDateTime.parse("2026-10-10T15:00:00"));
        assertThat(versions.getFirst().createdAt()).isEqualTo(versions.get(1).updatedAt());
    }

    @Test
    void historySkipsASaveThatLeftNothingInForce() {
        EmployeeSalaryComponent first = line(1L, travel, "4000", "2026-01-01", "2026-03-31");
        opened(first, "ayesha", "2026-01-01T09:00:00");
        closed(first, "ayesha", "2026-03-31T09:00:00");
        EmployeeSalaryComponent again = line(2L, travel, "5000", "2026-06-01", null);
        opened(again, "bilal", "2026-06-01T09:00:00");
        when(lines.findAllowanceLines(EMP)).thenReturn(List.of(first, again));

        List<AllowanceHistoryEntryDto> versions = service.history(EMP);

        assertThat(versions).extracting(AllowanceHistoryEntryDto::total)
                .containsExactly(new BigDecimal("5000"), new BigDecimal("4000"));
        // The removal save still closes the first version.
        assertThat(versions.get(1).updatedAt()).isEqualTo(LocalDateTime.parse("2026-03-31T09:00:00"));
    }

    @Test
    void lastChangedIsTheLatestStartOrCloseAcrossAllowances() {
        when(lines.findAllowanceLines(EMP)).thenReturn(List.of(
                line(1L, travel, "4000", "2026-01-01", "2026-07-01"),
                line(2L, travel, "4500", "2026-07-01", null),
                line(3L, mobile, "1500", "2026-07-01", "2026-09-01")));

        EmployeeAllowancesDto current = service.forEmployee(EMP);

        // Mobile was removed on Sep 1, after Travel's last change on Jul 1.
        assertThat(current.lastChanged()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(current.amounts()).containsOnlyKeys("TRAVEL");
        assertThat(current.since()).containsOnly(entry("TRAVEL", LocalDate.parse("2026-07-01")));
    }

    @Test
    void setClosesTodayAndOpensTodayOnlyWhatDiffers() {
        LocalDate today = LocalDate.now();
        EmployeeSalaryComponent openTravel = line(1L, travel, "4000", "2026-01-01", null);
        EmployeeSalaryComponent openMobile = line(2L, mobile, "1500", "2026-01-01", null);
        EmployeeSalaryComponent openSpecial = line(3L, special, "4000", "2026-01-01", null);
        when(lines.findOpenAllowanceLines(EMP)).thenReturn(List.of(openTravel, openMobile, openSpecial));

        service.set(EMP, new SetAllowancesRequest(Map.of(
                "TRAVEL", new BigDecimal("5000"),
                "MOBILE", BigDecimal.ZERO,
                "EDUCATION", new BigDecimal("2000"),
                "SPECIAL", new BigDecimal("4000.00"))));

        assertThat(openTravel.getEffectiveTo()).isEqualTo(today);
        assertThat(openMobile.getEffectiveTo()).isEqualTo(today);
        assertThat(openSpecial.getEffectiveTo()).isNull();

        ArgumentCaptor<EmployeeSalaryComponent> saved = ArgumentCaptor.forClass(EmployeeSalaryComponent.class);
        verify(lines, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(EmployeeSalaryComponent::getComponentId, EmployeeSalaryComponent::getAmount,
                        EmployeeSalaryComponent::getEffectiveFrom)
                .containsExactlyInAnyOrder(
                        tuple(1L, new BigDecimal("5000"), today),
                        tuple(2L, new BigDecimal("2000"), today));
        verify(lines, never()).delete(any());

        verify(salaryComponents).emit(EMP, travel, new BigDecimal("4000"), new BigDecimal("5000"), today, "UPDATED");
        verify(salaryComponents).emit(EMP, mobile, new BigDecimal("1500"), null, today, "REMOVED");
        verify(salaryComponents).emit(EMP, education, null, new BigDecimal("2000"), today, "ADDED");
        verify(salaryComponents, never()).emit(eq(EMP), eq(special), any(), any(), any(), any());
    }

    @Test
    void setOnALineFromTodayStillClosesAndInserts() {
        LocalDate today = LocalDate.now();
        LocalDate ahead = today.withDayOfMonth(1).plusMonths(1);
        EmployeeSalaryComponent travelToday = line(1L, travel, "4000", today.toString(), null);
        EmployeeSalaryComponent mobileAhead = line(2L, mobile, "1500", ahead.toString(), null);
        when(lines.findOpenAllowanceLines(EMP)).thenReturn(List.of(travelToday, mobileAhead));

        service.set(EMP, new SetAllowancesRequest(Map.of("TRAVEL", new BigDecimal("4500"))));

        // Same-day change: old line closed today, new line from today — never edited in place.
        assertThat(travelToday.getAmount()).isEqualByComparingTo("4000");
        assertThat(travelToday.getEffectiveTo()).isEqualTo(today);
        ArgumentCaptor<EmployeeSalaryComponent> saved = ArgumentCaptor.forClass(EmployeeSalaryComponent.class);
        verify(lines).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("4500");
        assertThat(saved.getValue().getEffectiveFrom()).isEqualTo(today);
        // A line month-dated ahead can't end before it starts: closed on its own start day.
        assertThat(mobileAhead.getEffectiveTo()).isEqualTo(ahead);
        verify(lines, never()).delete(any());
    }

    @Test
    void setRejectsUnknownCodes() {
        assertThatThrownBy(() -> service.set(EMP, new SetAllowancesRequest(Map.of("INCOME_TAX", BigDecimal.ONE))))
                .isInstanceOf(UnprocessableError.class);
        verify(salaryComponents, never()).emit(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownEmployeeIs404() {
        when(displayCache.get(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.forEmployee(7L)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void employeesFiltersAndOrdersByCardNo() {
        when(lines.findAllowanceLines()).thenReturn(List.of(
                line(1L, 100L, travel, "4000", "2026-01-01", null),
                line(2L, 101L, travel, "5000", "2026-02-01", null),
                line(3L, 101L, mobile, "1500", "2026-03-01", null),
                line(4L, 102L, travel, "6000", "2026-01-01", null),
                // Removed lines count toward lastChanged but not amounts.
                line(5L, 102L, mobile, "2000", "2026-01-01", "2026-04-30"),
                // Everything stopped: not in the grid.
                line(6L, 103L, travel, "3000", "2026-01-01", "2026-02-28")));
        when(displayCache.getMany(anyCollection())).thenReturn(Map.of(
                100L, display(100L, "100", "Asif Ali"),
                101L, display(101L, "27", "Zaheer Akram"),
                102L, display(102L, "4", "Khawar Ali")));

        PageResponse<EmployeeAllowanceRowDto> all = service.employees(null, PageRequest.of(0, 20));
        assertThat(all.content()).extracting(r -> r.employee().employeeCode()).containsExactly("4", "27", "100");
        EmployeeAllowanceRowDto zaheer = all.content().get(1);
        assertThat(zaheer.total()).isEqualByComparingTo("6500");
        assertThat(zaheer.lastChanged()).isEqualTo(LocalDate.parse("2026-03-01"));
        assertThat(zaheer.since())
                .containsEntry("TRAVEL", LocalDate.parse("2026-02-01"))
                .containsEntry("MOBILE", LocalDate.parse("2026-03-01"));
        EmployeeAllowanceRowDto khawar = all.content().get(0);
        assertThat(khawar.total()).isEqualByComparingTo("6000");
        assertThat(khawar.lastChanged()).isEqualTo(LocalDate.parse("2026-04-30"));

        PageResponse<EmployeeAllowanceRowDto> alis = service.employees(" ALI ", PageRequest.of(0, 20));
        assertThat(alis.content()).extracting(EmployeeAllowanceRowDto::employeeId).containsExactly(102L, 100L);
        assertThat(alis.totalElements()).isEqualTo(2);
    }

    private static SalaryComponent component(long id, String code, String name) {
        SalaryComponent c = new SalaryComponent();
        c.setId(id);
        c.setCode(code);
        c.setName(name);
        return c;
    }

    private static EmployeeSalaryComponent line(long id, SalaryComponent comp, String amount, String from, String to) {
        return line(id, EMP, comp, amount, from, to);
    }

    private static EmployeeSalaryComponent line(long id, long employeeId, SalaryComponent comp, String amount,
                                                String from, String to) {
        EmployeeSalaryComponent l = new EmployeeSalaryComponent();
        l.setId(id);
        l.setEmployeeId(employeeId);
        l.setComponentId(comp.getId());
        l.setAmount(new BigDecimal(amount));
        l.setEffectiveFrom(LocalDate.parse(from));
        l.setEffectiveTo(to == null ? null : LocalDate.parse(to));
        return l;
    }

    private static void opened(EmployeeSalaryComponent l, String by, String at) {
        l.setCreatedBy(by);
        l.setCreatedAt(LocalDateTime.parse(at));
    }

    private static void closed(EmployeeSalaryComponent l, String by, String at) {
        l.setUpdatedBy(by);
        l.setUpdatedAt(LocalDateTime.parse(at));
    }

    private static EmployeeDisplay display(long id, String code, String name) {
        return new EmployeeDisplay(id, code, name, null, "IT", null, "System Development", null, "Developer", null);
    }
}
