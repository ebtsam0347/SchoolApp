package com.forward.hrms.commons.approval.spi;

import java.util.Set;

/**
 * Declares which request type keys a consuming service supports.
 * Used by {@code ApprovalWorkflowService.listChains()} to enumerate
 * available types without depending on a shared enum.
 *
 * <p>Each consuming service provides one {@code @Bean} implementation.
 * Multiple beans are merged.
 */
public interface RequestTypeRegistration {
    Set<String> requestTypes();
}
