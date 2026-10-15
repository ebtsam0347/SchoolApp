package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.dto.request.EmployeeReportFilter;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Employee ID card ({@code jasper/employee-card.jrxml}): one CR80 card
 * (85.6 × 54 mm) per PDF page — letterhead, photo, card number, name,
 * parentage, department, section, designation, joining date and CNIC.
 * Prints one employee ({@link #render}) or everyone matching an
 * {@link EmployeeReportFilter} ({@link #renderMany}, capped at
 * {@value #MAX_CARDS} cards per run so a careless "all" can't pull every
 * photo into memory). Missing values print blank; a card is never refused
 * for incomplete data.
 */
@Service
@RequiredArgsConstructor
public class EmployeeCardService {

    static final int MAX_CARDS = 500;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final EmployeeReportScope scope;
    private final LetterSettingsService letterSettings;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;
    private final TransactionTemplate tx;

    /**
     * @param restrictToManagerId non-null when the caller is a manager; only a
     *        direct report's card may be printed (404 otherwise)
     */
    public byte[] render(long employeeId, Long restrictToManagerId) {
        Employee e = employees.findById(employeeId)
                .filter(emp -> restrictToManagerId == null || restrictToManagerId.equals(emp.getManagerId()))
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
        byte[] pdf = fill(List.of(e));
        tx.executeWithoutResult(status -> audit.emit("report", String.valueOf(employeeId), "employee-card.run",
                null, Map.of("employeeId", employeeId), true));
        return pdf;
    }

    /** Every employee matching the filter, one card per page, in card-number order. */
    public byte[] renderMany(EmployeeReportFilter filter, Long restrictToManagerId) {
        List<Employee> people = scope.select(filter, restrictToManagerId);
        if (people.isEmpty()) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED, "No employees match the filter.");
        }
        if (people.size() > MAX_CARDS) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "%d employees match; print at most %d cards at a time — narrow the filter (department, card range)."
                            .formatted(people.size(), MAX_CARDS));
        }
        byte[] pdf = fill(people);
        tx.executeWithoutResult(status -> audit.emit("report", "0", "employee-cards.run", null,
                Map.of("count", people.size(), "cards", people.stream().map(Employee::getEmployeeCode).toList()), true));
        return pdf;
    }

    private byte[] fill(List<Employee> people) {
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);
        Map<Long, Section> sectionById = byId(sections.findAll(), Section::getId);
        Map<Long, Designation> desigById = byId(designations.findAll(), Designation::getId);

        List<Map<String, ?>> rows = new ArrayList<>(people.size());
        for (Employee e : people) {
            Department d = e.getDepartmentId() == null ? null : deptById.get(e.getDepartmentId());
            Section s = e.getSectionId() == null ? null : sectionById.get(e.getSectionId());
            Designation g = e.getDesignationId() == null ? null : desigById.get(e.getDesignationId());
            Map<String, Object> m = new HashMap<>();
            m.put("employeeCode", e.getEmployeeCode());
            m.put("fullName", e.getFullName());
            m.put("relationLabel", relationLabel(e.getGender()));
            m.put("fatherName", e.getFatherName());
            m.put("department", d == null ? null : d.getName());
            m.put("section", s == null ? null : s.getName());
            m.put("designation", g == null ? null : g.getTitle());
            m.put("joiningDate", e.getJoiningDate() == null ? null : DATE.format(e.getJoiningDate()));
            m.put("cnic", e.getCnic());
            m.put("photo", letterSettings.employeePhoto(e.getProfilePictureKey()));
            rows.add(m);
        }

        LetterSettings settings = letterSettings.current();
        Map<String, Object> params = letterSettings.letterheadParams(settings, null, null);
        params.put("companyLogoBytes", bytes((InputStream) params.remove("companyLogo")));
        return jasper.renderPdf("/jasper/employee-card.jrxml", params, new JRMapCollectionDataSource(rows));
    }

    static String relationLabel(String gender) {
        String g = gender == null ? "" : gender.trim().toUpperCase(Locale.ROOT);
        return switch (g) {
            case "MALE", "M" -> "S/O";
            case "FEMALE", "F" -> "D/O";
            default -> "S/D/O";
        };
    }

    private static byte[] bytes(InputStream in) {
        if (in == null) return null;
        try (in) {
            return in.readAllBytes();
        } catch (IOException ex) {
            return null;
        }
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>(rows.size());
        for (T t : rows) m.put(idFn.apply(t), t);
        return m;
    }
}
