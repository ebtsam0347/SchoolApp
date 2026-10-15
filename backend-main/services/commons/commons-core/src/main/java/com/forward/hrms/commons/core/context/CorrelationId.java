package com.forward.hrms.commons.core.context;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ULID-style correlation id (26 chars Crockford base32) and a thread-local
 * holder. The web filter reads/writes the {@code X-Correlation-Id} header
 * and populates this; non-web code (Kafka consumers, scheduled jobs) sets
 * it manually before doing work.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationId() {}

    public static String generate() {
        long ts = System.currentTimeMillis();
        long r1 = ThreadLocalRandom.current().nextLong();
        long r2 = ThreadLocalRandom.current().nextLong();
        char[] out = new char[26];
        for (int i = 9; i >= 0; i--) {
            out[i] = CROCKFORD[(int) (ts & 0x1F)];
            ts >>>= 5;
        }
        for (int i = 10; i < 18; i++) {
            out[i] = CROCKFORD[(int) (r1 & 0x1F)];
            r1 >>>= 5;
        }
        for (int i = 18; i < 26; i++) {
            out[i] = CROCKFORD[(int) (r2 & 0x1F)];
            r2 >>>= 5;
        }
        return new String(out);
    }

    public static Optional<String> get() { return Optional.ofNullable(CURRENT.get()); }

    public static String getOrGenerate() {
        String v = CURRENT.get();
        if (v == null) {
            v = generate();
            CURRENT.set(v);
        }
        return v;
    }

    public static void set(String id) {
        if (id == null) clear(); else CURRENT.set(id);
    }

    public static void clear() { CURRENT.remove(); }
}
