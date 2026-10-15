package com.forward.hrms.commons.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.cache.client.EmployeeApiClient;
import com.forward.hrms.commons.cache.config.CommonsCacheProperties;
import com.forward.hrms.commons.cache.internal.CacheKeys;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.SectionDto;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

/**
 * The shared employee-display cache. The single seam every consumer
 * should use to resolve employee display fields by id.
 *
 * <h2>Read path</h2>
 * <ol>
 *   <li>{@code MGET hrms:cache:emp:{id}} for the requested ids.</li>
 *   <li>For ids that hit a tombstone — record absent (employee
 *       confirmed gone upstream).</li>
 *   <li>For ids that hit a real value — deserialize and add to result.</li>
 *   <li>For ids that miss — one bulk
 *       {@code GET /employees/by-ids?ids=…} (chunked at 200), join
 *       department/section/designation names from {@link CatalogCache},
 *       populate Redis (best-effort), return the merged map.</li>
 * </ol>
 *
 * <h2>Soft dependency</h2>
 * Every Redis call is wrapped: connection failures are logged and
 * treated as misses. Writes that fail are dropped silently — the next
 * read repopulates. The bulk-fetch path runs whether Redis is up,
 * down, or empty; the API contract is the same in all three cases.
 *
 * <h2>What's NOT here</h2>
 * No L1 in-process cache (Caffeine etc.) — Redis is fast enough at
 * this scale. No write-through — updates flow through the
 * {@code employee.events} consumer, not through this class.
 */
@Slf4j
public class EmployeeDisplayCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final EmployeeApiClient client;
    private final CatalogCache catalogs;
    private final CommonsCacheProperties props;

    public EmployeeDisplayCache(StringRedisTemplate redis,
                                ObjectMapper json,
                                EmployeeApiClient client,
                                CatalogCache catalogs,
                                CommonsCacheProperties props) {
        this.redis = redis;
        this.json = json;
        this.client = client;
        this.catalogs = catalogs;
        this.props = props;
    }

    /**
     * Resolve display fields for every id in the input. Missing ids
     * (deleted / never existed) are absent from the returned map; the
     * caller handles them with whatever placeholder makes sense for
     * the surface.
     */
    public Map<Long, EmployeeDisplay> getMany(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Set<Long> distinct = new java.util.LinkedHashSet<>(ids);
        Map<Long, EmployeeDisplay> out = new LinkedHashMap<>(distinct.size());

        Set<Long> needFetch = new HashSet<>(distinct);
        List<String> hits = mgetSafe(distinct);
        if (hits != null) {
            int i = 0;
            for (Long id : distinct) {
                String raw = hits.get(i++);
                if (raw == null) continue;                  // miss
                if (CacheKeys.TOMBSTONE.equals(raw)) {      // confirmed gone
                    needFetch.remove(id);
                    continue;
                }
                try {
                    EmployeeDisplay v = json.readValue(raw, EmployeeDisplay.class);
                    out.put(id, v);
                    needFetch.remove(id);
                } catch (Exception ex) {
                    log.warn("commons-cache: corrupt cached emp {}, refetching: {}", id, ex.getMessage());
                }
            }
        }

        if (needFetch.isEmpty()) return out;

        Map<Long, EmployeeDto> fetched = client.getEmployeesByIds(needFetch);
        Map<Long, DepartmentDto> deps = indexById(catalogs.departments(), DepartmentDto::id);
        Map<Long, SectionDto> secs = indexById(catalogs.sections(), SectionDto::id);
        Map<Long, DesignationDto> desigs = indexById(catalogs.designations(), DesignationDto::id);

        for (Long id : needFetch) {
            EmployeeDto e = fetched.get(id);
            if (e == null) {
                trySetTombstone(id);
                continue;
            }
            EmployeeDisplay v = resolve(e, deps, secs, desigs);
            out.put(id, v);
            tryPut(id, v);
        }
        return out;
    }

    /** Single-id convenience. Returns null when the id resolves to nothing. */
    public EmployeeDisplay get(long id) {
        return getMany(List.of(id)).get(id);
    }

    /**
     * Drop the cached entry for an id so the next read refetches.
     * Intended for the events consumer; callers shouldn't need it.
     */
    public void invalidate(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        try {
            List<String> keys = ids.stream().map(CacheKeys::employee).toList();
            redis.delete(keys);
        } catch (DataAccessException ex) {
            log.warn("commons-cache: redis delete {} keys failed: {}", ids.size(), ex.getMessage());
        }
    }

    /**
     * Write a fully-resolved display row directly. Used by the events
     * consumer when it's already done the catalog joins itself.
     */
    public void put(EmployeeDisplay value) {
        if (value == null) return;
        tryPut(value.employeeId(), value);
    }

    /** Mark an id as confirmed-gone (tombstone) so we don't refetch on every render. */
    public void tombstone(long id) {
        trySetTombstone(id);
    }

    // ===== internals =====

    private EmployeeDisplay resolve(EmployeeDto e,
                                    Map<Long, DepartmentDto> deps,
                                    Map<Long, SectionDto> secs,
                                    Map<Long, DesignationDto> desigs) {
        DepartmentDto d = e.departmentId() == null ? null : deps.get(e.departmentId());
        SectionDto s = e.sectionId() == null ? null : secs.get(e.sectionId());
        DesignationDto g = e.designationId() == null ? null : desigs.get(e.designationId());
        return new EmployeeDisplay(
                e.id(), e.employeeCode(), e.fullName(),
                e.departmentId(), d == null ? null : d.name(),
                e.sectionId(),    s == null ? null : s.name(),
                e.designationId(), g == null ? null : g.title(),
                e.profilePictureUrl());
    }

    private static <T> Map<Long, T> indexById(List<T> rows, java.util.function.ToLongFunction<T> idOf) {
        if (rows == null || rows.isEmpty()) return Map.of();
        Map<Long, T> out = new HashMap<>(rows.size());
        for (T r : rows) out.put(idOf.applyAsLong(r), r);
        return out;
    }

    private List<String> mgetSafe(Collection<Long> ids) {
        try {
            List<String> keys = new ArrayList<>(ids.size());
            for (Long id : ids) keys.add(CacheKeys.employee(id));
            return redis.opsForValue().multiGet(keys);
        } catch (DataAccessException ex) {
            log.warn("commons-cache: redis mget {} keys failed: {}", ids.size(), ex.getMessage());
            return null;
        }
    }

    private void tryPut(long id, EmployeeDisplay v) {
        try {
            String body = json.writeValueAsString(v);
            redis.opsForValue().set(CacheKeys.employee(id), body,
                    Duration.ofSeconds(props.employeeTtlSeconds()));
        } catch (DataAccessException ex) {
            log.warn("commons-cache: redis put emp {} failed: {}", id, ex.getMessage());
        } catch (Exception ex) {
            log.warn("commons-cache: serialize emp {} failed: {}", id, ex.getMessage());
        }
    }

    private void trySetTombstone(long id) {
        try {
            redis.opsForValue().set(CacheKeys.employee(id), CacheKeys.TOMBSTONE,
                    Duration.ofSeconds(props.tombstoneTtlSeconds()));
        } catch (DataAccessException ex) {
            log.warn("commons-cache: redis tombstone emp {} failed: {}", id, ex.getMessage());
        }
    }
}
