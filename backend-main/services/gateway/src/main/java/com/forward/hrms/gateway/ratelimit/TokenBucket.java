package com.forward.hrms.gateway.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process token bucket. {@link #tryConsume()} returns 0 when a token
 * was granted, or the wait-millis until the next token would be available.
 *
 * <p>Lock-free: state is encoded as the system-nanos at which the bucket
 * was last full minus {@code (capacity - tokensAvailable) / refillRate}.
 * One CAS per consume — uncontested per-bucket (each user / IP gets its
 * own bucket).
 *
 * <p>Extracted from {@code SearchRateLimitFilter.Bucket} so the per-IP
 * (§6.4) and per-user generic (§6.5) filters can share the same engine.
 */
public final class TokenBucket {

    private final long capacity;
    private final double tokensPerNano;
    private final AtomicLong stateRef;

    public TokenBucket(int capacity, double refillPerSecond) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (!(refillPerSecond > 0)) throw new IllegalArgumentException("refillPerSecond must be > 0");
        this.capacity = capacity;
        this.tokensPerNano = refillPerSecond / 1_000_000_000d;
        this.stateRef = new AtomicLong(System.nanoTime() - (long) (capacity / tokensPerNano));
    }

    /**
     * @return 0 if a token was consumed; otherwise the millis to wait
     *         before the next attempt would succeed.
     */
    public long tryConsume() {
        while (true) {
            long state = stateRef.get();
            long now = System.nanoTime();
            double tokens = Math.min(capacity, (now - state) * tokensPerNano);
            if (tokens >= 1.0) {
                long newState = now - (long) ((capacity - (tokens - 1)) / tokensPerNano);
                if (stateRef.compareAndSet(state, newState)) return 0L;
            } else {
                double need = 1.0 - tokens;
                long waitNanos = (long) Math.ceil(need / tokensPerNano);
                return Math.max(1L, waitNanos / 1_000_000L);
            }
        }
    }
}
