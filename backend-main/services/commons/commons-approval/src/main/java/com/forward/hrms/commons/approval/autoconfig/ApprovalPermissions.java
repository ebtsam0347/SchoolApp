package com.forward.hrms.commons.approval.autoconfig;

/**
 * Permission strings for the approval module, bound from
 * {@code hrms.approval.permissions.*} by the consuming service.
 * Each service supplies its own namespace (e.g. {@code attendance.read},
 * {@code payroll.read}) so the shared controllers stay domain-neutral.
 */
public record ApprovalPermissions(String read, String write, String admin, String override) {}
