package com.forward.hrms.commons.cache.client;

import com.forward.hrms.commons.cache.config.CommonsCacheProperties;
import com.forward.hrms.employee.dto.response.CityDto;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.dto.response.ProvinceDto;
import com.forward.hrms.employee.dto.response.QualificationDto;
import com.forward.hrms.employee.dto.response.SectionDto;
import com.forward.hrms.employee.dto.response.SkillDto;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin employee-service client for the cache miss path. Hits
 * {@code /employees/by-ids} (chunked at 200) plus the eight catalog
 * endpoints. The bulk read is gated on authentication only — every
 * service using the cache (attendance operator dashboard, payroll
 * payslip generation, etc.) needs to resolve display names without
 * pulling the broader {@code employee.read} permission.
 */
@Slf4j
public class EmployeeApiClient {

    private static final int BY_IDS_CHUNK = 200;

    private final RestClient http;

    public EmployeeApiClient(CommonsCacheProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.employeeServiceUrl()).build();
    }

    public Map<Long, EmployeeDto> getEmployeesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<Long> distinct = ids.stream().distinct().toList();
        Map<Long, EmployeeDto> out = new LinkedHashMap<>();
        for (int i = 0; i < distinct.size(); i += BY_IDS_CHUNK) {
            List<Long> chunk = distinct.subList(i, Math.min(i + BY_IDS_CHUNK, distinct.size()));
            String csv = chunk.stream().map(String::valueOf).collect(Collectors.joining(","));
            try {
                List<EmployeeDto> rows = http.get()
                        .uri(uri -> uri.path("/employees/by-ids").queryParam("ids", csv).build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<EmployeeDto>>() {});
                if (rows == null) continue;
                for (EmployeeDto v : rows) out.put(v.id(), v);
            } catch (RestClientException ex) {
                // Bulk fetch is the cache's miss path — propagate so the caller knows
                // the read genuinely failed (employee-service down). Cache layer
                // upstream catches and returns identity-only placeholders.
                throw ex;
            }
        }
        return out;
    }

    public List<DepartmentDto> departments() {
        return getList("/departments", new ParameterizedTypeReference<>() {});
    }

    public List<SectionDto> sections() {
        return getList("/sections", new ParameterizedTypeReference<>() {});
    }

    public List<DesignationDto> designations() {
        return getList("/designations", new ParameterizedTypeReference<>() {});
    }

    public List<GradeDto> grades() {
        return getList("/grades", new ParameterizedTypeReference<>() {});
    }

    public List<ProvinceDto> provinces() {
        return getList("/provinces", new ParameterizedTypeReference<>() {});
    }

    public List<CityDto> cities() {
        return getList("/cities", new ParameterizedTypeReference<>() {});
    }

    public List<QualificationDto> qualifications() {
        return getList("/qualifications", new ParameterizedTypeReference<>() {});
    }

    public List<SkillDto> skills() {
        return getList("/skills", new ParameterizedTypeReference<>() {});
    }

    private <T> List<T> getList(String path, ParameterizedTypeReference<List<T>> type) {
        List<T> body = http.get().uri(path).retrieve().body(type);
        return body == null ? List.of() : body;
    }
}
