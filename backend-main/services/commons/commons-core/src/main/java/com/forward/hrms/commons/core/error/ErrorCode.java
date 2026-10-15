package com.forward.hrms.commons.core.error;

/**
 * Stable, machine-readable error code. Each service ships an enum
 * implementing this — `Enum.name()` satisfies the contract.
 */
public interface ErrorCode {
    String name();
}
