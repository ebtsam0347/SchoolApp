package com.forward.hrms.commons.saga;

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexes every {@link Saga} bean by {@link Saga#name()} so the runner can
 * resolve a saga from a row. Built once at startup; saga set is fixed for
 * the lifetime of the JVM.
 */
@RequiredArgsConstructor
public class SagaRegistry {

    private final List<Saga> sagas;
    private Map<String, Saga> byName;

    public Saga byName(String name) {
        if (byName == null) index();
        return byName.get(name);
    }

    private synchronized void index() {
        if (byName != null) return;
        Map<String, Saga> m = new HashMap<>();
        for (Saga s : sagas) {
            Saga prev = m.put(s.name(), s);
            if (prev != null) {
                throw new IllegalStateException("Duplicate saga name: " + s.name()
                        + " (" + prev.getClass() + " vs " + s.getClass() + ")");
            }
        }
        byName = Map.copyOf(m);
    }
}
