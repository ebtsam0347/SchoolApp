package com.forward.hrms.bff.config;

import com.forward.hrms.commons.web.async.RequestPropagatingExecutor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Service-level wiring that doesn't fit any other bucket.
 *
 * <p>{@link #bffFanOutExecutor()} is a per-task virtual-thread executor
 * the composite services use to run downstream calls in parallel. We
 * already have {@code spring.threads.virtual.enabled=true} for the
 * Tomcat request thread, but composing 3–5 blocking RestClient calls
 * still needs a separate executor to fan them out — the request thread
 * itself can only do one at a time.
 */
@Configuration
@EnableConfigurationProperties({DownstreamProperties.class, StreamProperties.class})
public class BffConfig {

    /**
     * §7.bff #43 — typed bean. {@code @EnableScheduling} also registers
     * an {@code AsyncTaskExecutor} (the scheduler), so injection points
     * used to disambiguate via {@code @Qualifier("bffFanOutExecutor")}.
     * Returning the {@link FanOutExecutor} sub-interface lets Spring
     * resolve by type — qualifier strings are no longer required.
     */
    @Bean
    public FanOutExecutor bffFanOutExecutor() {
        TaskExecutorAdapter delegate = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
        return new FanOutExecutor() {
            @Override public void execute(Runnable task) { delegate.execute(task); }
            @Override public Future<?> submit(Runnable task) { return delegate.submit(task); }
            @Override public <T> Future<T> submit(Callable<T> task) { return delegate.submit(task); }
            @Override public <T> CompletableFuture<T> submitCompletable(Callable<T> task) {
                return delegate.submitCompletable(task);
            }
            @Override public CompletableFuture<Void> submitCompletable(Runnable task) {
                return delegate.submitCompletable(task);
            }
        };
    }

    /**
     * §7.bff #1 — single shared request-propagating wrapper around the
     * fan-out executor. BFF services use this to run downstream calls
     * with the inbound request's identity rebound on the worker thread,
     * replacing the {@code supplyWithRequest} copy that used to live in
     * every composite.
     */
    @Bean
    public RequestPropagatingExecutor bffPropagating(FanOutExecutor executor) {
        return new RequestPropagatingExecutor(executor);
    }

    /**
     * One Redis listener container shared across all SSE connections.
     * Per-user listeners are added/removed dynamically by
     * {@code NotificationStreamService}. The container auto-starts on
     * context refresh and stops on shutdown so subscriptions unwind
     * cleanly.
     */
    @Bean
    public RedisMessageListenerContainer notificationsListenerContainer(RedisConnectionFactory cf) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        return container;
    }
}
