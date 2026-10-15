package com.forward.hrms.auth.service;

import java.util.Map;
import java.util.Set;

/**
 * Canonical registry of every permission code in the system, with its UI
 * group and human description. This is the single source of truth the role
 * editor reads; {@code PermissionRegistryTest} asserts it stays in sync with
 * the seed migrations and with every {@code @RequirePermission} literal across
 * the services, so a code that's enforced-but-unseeded (or vice-versa) fails
 * the build instead of shipping a dead endpoint.
 */
public final class PermissionCatalog {

    // Map.ofEntries, not Map.of — the latter caps at 10 pairs.
    private static final Map<String, String> NAMESPACE_GROUPS = Map.ofEntries(
            Map.entry("auth.users",    "Identity"),
            Map.entry("auth.roles",    "Identity"),
            Map.entry("auth.sessions", "Identity"),
            Map.entry("audit",         "Compliance"),
            Map.entry("employee",      "People"),
            Map.entry("team.request.generation", "People"),
            Map.entry("attendance",    "Time & Leave"),
            Map.entry("canteen",       "Time & Leave"),
            Map.entry("travel",        "Travel"),
            Map.entry("payroll",       "Payroll"),
            Map.entry("notification",  "Messaging"),
            Map.entry("chat",          "Messaging")
    );

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            // auth
            Map.entry("auth.users.read",     "Read user accounts and their assigned roles."),
            Map.entry("auth.users.write",    "Create, update, lock, and unlock user accounts."),
            Map.entry("auth.users.reset",    "Force-reset another user's password."),
            Map.entry("auth.roles.read",     "Read the role and permission catalog."),
            Map.entry("auth.roles.write",    "Create, edit, and delete custom roles."),
            Map.entry("auth.sessions.admin", "View and revoke any active session."),
            // audit
            Map.entry("audit.read",          "Read the full audit log and entity timelines."),
            Map.entry("audit.admin",         "Replay Kafka topics and view consumer health."),
            // employee
            Map.entry("employee.read",           "Read employees, org structure, catalogs, and history."),
            Map.entry("employee.write",          "Create / update employees, catalogs, and lifecycle events."),
            Map.entry("employee.admin",          "Retry employee sagas, grant or revoke ESS access."),
            Map.entry("employee.reports.run",    "Run employee-service standard reports."),
            Map.entry("employee.analytics.read", "Read people analytics."),
            // factory tours (auth V0029)
            Map.entry("employee.tours.write",                 "Create and submit factory tour requests."),
            Map.entry("employee.tours.local.approve",         "Approve local factory tour requests (Deputy Director)."),
            Map.entry("employee.tours.international.approve", "Approve international factory tour requests (D1 Director)."),
            // travel (auth V0032 / V0033 / V0035)
            Map.entry("travel.read",               "Read travel requests, expenses, advances, settlements and policies."),
            Map.entry("travel.write",              "Raise and manage own travel requests, expenses, advances, settlements."),
            Map.entry("travel.approve",            "HR-approve travel requests and review travel expenses."),
            Map.entry("travel.advance.approve",    "Approve, pay and reconcile travel advances (Finance)."),
            Map.entry("travel.settlement.approve", "HR settlement verification — stage 1 of the settlement chain."),
            Map.entry("travel.settlement.paid",    "Accounts payment confirmation — stage 2 of the settlement chain."),
            Map.entry("travel.transport.verify",   "Verify a travel request's company vehicle and per-leg distance."),
            Map.entry("travel.hr.approve",         "Reserved — not used by any active Travel approval stage."),
            Map.entry("travel.admin",              "Administer versioned travel policies and grade limits."),
            // canteen (auth V0031)
            Map.entry("canteen.read",   "Read the canteen (meal) register."),
            Map.entry("canteen.create", "Add a canteen (meal) entry."),
            Map.entry("canteen.write",  "Edit a canteen (meal) entry."),
            Map.entry("canteen.delete", "Delete a canteen (meal) entry."),
            // team request generation
            Map.entry("team.request.generation.leave",            "Create team leave requests."),
            Map.entry("team.request.generation.shortLeave",       "Create team short-leave requests."),
            Map.entry("team.request.generation.shortTermAdvance", "Create team short-term-advance requests."),
            Map.entry("team.request.generation.longTermAdvance",  "Create team long-term-advance requests."),
            Map.entry("team.request.generation.regularization",   "Create team attendance-regularization requests."),
            Map.entry("team.request.generation.overtime",         "Create team overtime requests."),
            Map.entry("team.request.generation.requisition",      "Create team requisition requests."),
            Map.entry("team.request.generation.evaluation",       "Create team evaluation requests."),
            // attendance (leaves re-homed under attendance.leaves.*)
            Map.entry("attendance.read",                 "Read shifts, attendance, leaves, OT, and regularizations."),
            Map.entry("attendance.write",                "Self-serve punch / leave / OT / regularization apply."),
            Map.entry("attendance.admin",                "Manage shift catalog, holidays, balances; force operations."),
            Map.entry("attendance.operator",             "Operator punch + shift corrections for others (add / amend / shift-override)."),
            Map.entry("attendance.overtime.bulk",        "Bulk overtime submission for a department."),
            Map.entry("attendance.hr.approve",           "Final-stage HR approval on leave / OT / regularization."),
            Map.entry("attendance.approval.override",    "Force-finalize a request; delegate on behalf of others."),
            Map.entry("attendance.poller",               "attendance-poller service account — ingest punches, drain sync queue, push templates."),
            Map.entry("attendance.reports.run",          "Run attendance-service standard reports."),
            Map.entry("attendance.analytics.read",       "Read attendance analytics."),
            Map.entry("attendance.devices.admin",        "Manage biometric / kiosk devices."),
            Map.entry("attendance.leaves.reports.run",   "Run leave reports."),
            Map.entry("attendance.leaves.analytics.read","Read leave analytics."),
            // payroll
            Map.entry("payroll.read",            "Read payroll runs, payslips, salary structure, off-cycle payments."),
            Map.entry("payroll.write",           "Submit off-cycle payments and capture payroll inputs."),
            Map.entry("payroll.admin",           "Run payroll batches; manage catalogs, statutory config, increments."),
            Map.entry("payroll.reports.run",     "Run payroll-service standard reports."),
            Map.entry("payroll.analytics.read",  "Read payroll analytics."),
            Map.entry("payroll.read.team",       "Read payslips of the caller's direct reports."),
            Map.entry("payroll.advance.hr",      "HR-approve / reject employee advances."),
            Map.entry("payroll.advance.accounts","Accounts-approve / disburse / reject employee advances."),
            Map.entry("payroll.offcycle.accounts","Accounts: pay an HR-approved off-cycle payment."),
            Map.entry("payroll.settlement.accounts","Accounts: pay an HR-approved employee final settlement."),
            // notification
            Map.entry("notification.template.read",  "Read notification rendering templates."),
            Map.entry("notification.template.write", "Create / update notification templates."),
            Map.entry("notification.announce",       "Broadcast a notification to a user-id list."),
            // chat
            Map.entry("chat.broadcast.write",    "Post to the org-wide announcements channel."),
            Map.entry("chat.conversations.read", "Read any conversation (admin / HR review)."),
            Map.entry("chat.messages.delete",    "Hard-delete any chat message.")
    );

    private PermissionCatalog() {}

    /** Every permission code known to the system. */
    public static Set<String> all() {
        return DESCRIPTIONS.keySet();
    }

    public static String groupOf(String code) {
        if (code == null) return "Other";
        // Match against progressively shorter prefixes — "auth.users.read" → "auth.users".
        int lastDot = code.lastIndexOf('.');
        while (lastDot > 0) {
            String prefix = code.substring(0, lastDot);
            String group = NAMESPACE_GROUPS.get(prefix);
            if (group != null) return group;
            lastDot = code.lastIndexOf('.', lastDot - 1);
        }
        return "Other";
    }

    public static String descriptionOf(String code) {
        if (code == null) return "";
        return DESCRIPTIONS.getOrDefault(code, code);
    }
}
