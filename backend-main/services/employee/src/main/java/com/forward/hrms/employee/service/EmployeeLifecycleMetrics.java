package com.forward.hrms.employee.service;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class EmployeeLifecycleMetrics {

    private final MeterRegistry meters;

    public void onboardStarted() {
        counter("hrms.employee.onboard", "result", "started").increment();
    }

    public void onboardFailed(String reason) {
        counter("hrms.employee.onboard", "result", "failed", "reason", reason).increment();
    }

    public void resignSubmitted() {
        counter("hrms.employee.resign", "result", "submitted").increment();
    }

    public void terminated(String kind) {
        counter("hrms.employee.terminate", "kind", kind).increment();
    }

    public void reinstated(String kind) {
        counter("hrms.employee.reinstate", "kind", kind).increment();
    }

    public void essProvisioned() {
        counter("hrms.employee.ess", "result", "provisioned").increment();
    }

    public void essRevoked() {
        counter("hrms.employee.ess", "result", "revoked").increment();
    }

    private Counter counter(String name, String... tags) {
        return meters.counter(name, tags);
    }
}
