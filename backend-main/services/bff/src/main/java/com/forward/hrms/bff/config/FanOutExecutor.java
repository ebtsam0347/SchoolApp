package com.forward.hrms.bff.config;

import org.springframework.core.task.AsyncTaskExecutor;

/**
 * §7.bff #43 — typed marker for the BFF's per-task virtual-thread
 * fan-out executor. The {@code AsyncTaskExecutor} bean was previously
 * resolved via {@code @Qualifier("bffFanOutExecutor")} on every
 * injection point because Spring's {@code @EnableScheduling} also
 * registers an {@code AsyncTaskExecutor}. With a sub-interface, Spring
 * resolves by type and the qualifier strings disappear.
 */
public interface FanOutExecutor extends AsyncTaskExecutor {
}
