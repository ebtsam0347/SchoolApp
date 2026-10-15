package com.forward.hrms.commons.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.cache.client.EmployeeApiClient;
import com.forward.hrms.commons.cache.config.CommonsCacheProperties;
import com.forward.hrms.commons.cache.internal.CacheKeys;
import com.forward.hrms.employee.dto.response.CityDto;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.dto.response.ProvinceDto;
import com.forward.hrms.employee.dto.response.QualificationDto;
import com.forward.hrms.employee.dto.response.SectionDto;
import com.forward.hrms.employee.dto.response.SkillDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * Typed accessors for the eight employee-service catalogs (departments,
 * sections, designations, grades, provinces, cities, qualifications,
 * skills). Each returns the live list, transparently backed by Redis.
 *
 * <p>Soft dependency: every Redis read or write that throws
 * {@link RedisConnectionFailureException} or {@link DataAccessException}
 * falls through to the HTTP client (and skips the populate step on
 * read-back failure). The cache is a perf optimisation, not a hard
 * dependency — services keep working at HTTP-fan-out speed when Redis
 * is down.
 */
@Slf4j
@RequiredArgsConstructor
public class CatalogCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final EmployeeApiClient client;
    private final CommonsCacheProperties props;

    public List<DepartmentDto> departments() {
        return load(CacheKeys.CAT_DEPARTMENTS, new TypeReference<>() {}, client::departments);
    }

    public List<SectionDto> sections() {
        return load(CacheKeys.CAT_SECTIONS, new TypeReference<>() {}, client::sections);
    }

    public List<DesignationDto> designations() {
        return load(CacheKeys.CAT_DESIGNATIONS, new TypeReference<>() {}, client::designations);
    }

    public List<GradeDto> grades() {
        return load(CacheKeys.CAT_GRADES, new TypeReference<>() {}, client::grades);
    }

    public List<ProvinceDto> provinces() {
        return load(CacheKeys.CAT_PROVINCES, new TypeReference<>() {}, client::provinces);
    }

    public List<CityDto> cities() {
        return load(CacheKeys.CAT_CITIES, new TypeReference<>() {}, client::cities);
    }

    public List<QualificationDto> qualifications() {
        return load(CacheKeys.CAT_QUALIFICATIONS, new TypeReference<>() {}, client::qualifications);
    }

    public List<SkillDto> skills() {
        return load(CacheKeys.CAT_SKILLS, new TypeReference<>() {}, client::skills);
    }

    /**
     * One-shot id-keyed view of the five catalogs every employee row
     * needs (department / section / designation / grade / city).
     * Provinces / qualifications / skills aren't included — they don't
     * appear on the directory or detail rows. Saves the caller from
     * indexing each list itself, and means each catalog is fetched
     * exactly once per render.
     */
    public CatalogIndex snapshot() {
        return new CatalogIndex(
                indexById(departments(), DepartmentDto::id),
                indexById(sections(), SectionDto::id),
                indexById(designations(), DesignationDto::id),
                indexById(grades(), GradeDto::id),
                indexById(cities(), CityDto::id));
    }

    public record CatalogIndex(
            Map<Long, DepartmentDto> departments,
            Map<Long, SectionDto> sections,
            Map<Long, DesignationDto> designations,
            Map<Long, GradeDto> grades,
            Map<Long, CityDto> cities
    ) {}

    private static <T> Map<Long, T> indexById(List<T> rows, ToLongFunction<T> idOf) {
        if (rows == null || rows.isEmpty()) return Map.of();
        Map<Long, T> out = new HashMap<>(rows.size() * 2);
        for (T r : rows) out.put(idOf.applyAsLong(r), r);
        return out;
    }

    /** Wipe a single catalog key so the next read repopulates from upstream. */
    public void invalidate(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException ex) {
            log.warn("commons-cache: redis delete {} failed: {}", key, ex.getMessage());
        }
    }

    private <T> List<T> load(String key, TypeReference<List<T>> type, Supplier<List<T>> miss) {
        String hit = tryGet(key);
        if (hit != null) {
            try {
                return json.readValue(hit, type);
            } catch (Exception ex) {
                log.warn("commons-cache: corrupt cached value at {}, refetching: {}", key, ex.getMessage());
            }
        }
        List<T> fresh = miss.get();
        tryPut(key, fresh);
        return fresh;
    }

    private String tryGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException ex) {
            log.warn("commons-cache: redis get {} failed: {}", key, ex.getMessage());
            return null;
        }
    }

    private void tryPut(String key, Object value) {
        try {
            String body = json.writeValueAsString(value);
            redis.opsForValue().set(key, body, Duration.ofSeconds(props.catalogTtlSeconds()));
        } catch (DataAccessException ex) {
            log.warn("commons-cache: redis put {} failed: {}", key, ex.getMessage());
        } catch (Exception ex) {
            log.warn("commons-cache: serialize {} failed: {}", key, ex.getMessage());
        }
    }
}
