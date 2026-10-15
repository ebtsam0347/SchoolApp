package com.forward.hrms.notification.service;

import com.forward.hrms.notification.enums.NotificationType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single entry-point Kafka consumers funnel into. Routes the inbound
 * envelope to a per-event-type handler that resolves the recipient(s),
 * builds the template variable map, and calls
 * {@link NotificationService#dispatch(long, String, Map)}.
 *
 * <p>Recipient resolution is intentionally simple — every payload carries
 * the id we need (employeeId / userId, plus stage-specific approver ids
 * from attendance's approval mechanism). When resolution needs role-based
 * fan-out (HR-style stages), the producing service is expected to push
 * the resolved {@code assignedTo} into the payload first; this service
 * does not call out to the org graph.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDispatcher {

    private final EventDedupeStore dedupe;
    private final NotificationService notifications;
    private final RoleMembershipService roles;
    private final ObjectMapper json;

    @Transactional
    public void handle(String topic, String message) {
        JsonNode root;
        try {
            root = json.readTree(message);
        } catch (Exception e) {
            log.warn("Skipping malformed envelope from {}: {}", topic, e.getMessage());
            return;
        }
        String eventId = text(root, "eventId");
        String eventType = text(root, "eventType");
        if (eventType == null || eventType.isBlank()) {
            log.warn("Envelope from {} has no eventType; skipping. eventId={}", topic, eventId);
            return;
        }
        if (!dedupe.claim(eventId, topic, eventType)) {
            log.debug("Duplicate event {} on {}", eventId, topic);
            return;
        }
        JsonNode payload = root.path("payload");
        try {
            switch (eventType) {
                // ---- Leave ----
                case "leave.applied" -> onLeaveApplied(payload);
                case "leave.stage_approved" -> onLeaveStageApproved(payload);
                case "leave.stage_rejected" -> onLeaveStageRejected(payload);
                case "leave.cancelled" -> onLeaveCancelled(payload);
                case "leave.request_finalized" -> onLeaveFinalized(payload);
                // day-level approvals are intentionally not surfaced (too chatty).
                case "leave.day_approved", "leave.day_rejected" -> { }

                // ---- Overtime ----
                case "overtime.requested" -> onOtRequested(payload);
                case "overtime.stage_approved" -> onOtStageApproved(payload);
                case "overtime.stage_rejected" -> onOtStageRejected(payload);
                case "overtime.approved" -> onOtFinalized(payload, true);
                case "overtime.rejected" -> onOtFinalized(payload, false);

                // ---- Regularization ----
                case "regularization.requested" -> onRegRequested(payload);
                case "regularization.stage_approved" -> onRegStageApproved(payload);
                case "regularization.stage_rejected" -> onRegStageRejected(payload);
                case "regularization.approved" -> onRegFinalized(payload, true);
                case "regularization.rejected" -> onRegFinalized(payload, false);

                // ---- Payroll ----
                case "payroll.payslip.generated" -> onPayslipGenerated(payload);

                // ---- Auth ----
                case "auth.user.password_reset_by_admin" -> onPasswordResetByAdmin(payload);
                case "auth.session.revoked" -> onSessionRevoked(payload);
                // Bug #7 — role_members projection updates. No bell row emitted;
                // these only mutate the projection used for HR-style fan-out.
                case "auth.user.role_assigned" -> onRoleAssigned(payload);
                case "auth.user.role_revoked" -> onRoleRevoked(payload);

                // ---- Employee ----
                case "employee.manager_changed" -> onManagerChanged(payload);
                case "employee.suspended" -> onEmployeeSuspended(payload);
                case "employee.suspension_lifted" -> onSuspensionLifted(payload);

                // ---- Attendance ----
                case "attendance.shift_overridden" -> onShiftOverridden(payload);

                // ---- Chat ----
                case "chat.message.sent" -> onChatMessageSent(payload);
                // edited / deleted are SPA cache invalidations only — no bell.
                case "chat.message.edited", "chat.message.deleted" -> { }

                default -> log.debug("Ignoring event type {} from {}", eventType, topic);
            }
        } catch (RuntimeException e) {
            // Log + swallow so a malformed payload doesn't kill the consumer.
            // Kafka offset is still committed (idempotency table tracks the eventId).
            log.warn("Failed to dispatch {} (id={}): {}", eventType, eventId, e.getMessage(), e);
        }
    }

    // ----- Leave -----

    private void onLeaveApplied(JsonNode p) {
        Long approver = approverFromFirstStage(p);
        if (approver == null) return;
        Map<String, Object> vars = leaveVars(p);
        notifications.dispatch(approver, NotificationType.LEAVE_APPLIED, vars);
    }

    private void onLeaveStageApproved(JsonNode p) {
        // Skip when terminal (no next stage) — the request_finalized event covers it.
        if (p.path("nextStage").isMissingNode() || p.path("nextStage").isNull()) return;
        Map<String, Object> vars = leaveVars(p);
        vars.put("stageNo", asString(p, "stageNo"));
        dispatchToNextStage(p.path("nextStage"), NotificationType.LEAVE_STAGE_APPROVED, vars,
                "leave.stage_approved");
    }

    private void onLeaveStageRejected(JsonNode p) {
        Long submitter = readLong(p, "employeeId");
        if (submitter == null) return;
        Map<String, Object> vars = leaveVars(p);
        vars.put("stageNo", asString(p, "stageNo"));
        notifications.dispatch(submitter, NotificationType.LEAVE_STAGE_REJECTED, vars);
    }

    private void onLeaveCancelled(JsonNode p) {
        Long approver = readLong(p.path("currentStage"), "assignedTo");
        if (approver == null) approver = approverFromFirstStage(p);
        if (approver == null) return;
        Map<String, Object> vars = leaveVars(p);
        notifications.dispatch(approver, NotificationType.LEAVE_STAGE_REJECTED, vars);
    }

    private void onLeaveFinalized(JsonNode p) {
        Long submitter = readLong(p, "employeeId");
        if (submitter == null) return;
        String status = asString(p, "finalStatus");
        String type = switch (status == null ? "" : status.toUpperCase()) {
            case "APPROVED" -> NotificationType.LEAVE_APPROVED;
            case "PARTIALLY_APPROVED" -> NotificationType.LEAVE_PARTIALLY_APPROVED;
            case "REJECTED" -> NotificationType.LEAVE_REJECTED;
            default -> null;
        };
        if (type == null) return;
        Map<String, Object> vars = leaveVars(p);
        notifications.dispatch(submitter, type, vars);
    }

    // ----- Overtime -----

    private void onOtRequested(JsonNode p) {
        Long approver = approverFromFirstStage(p);
        if (approver == null) return;
        notifications.dispatch(approver, NotificationType.OT_REQUESTED, otVars(p));
    }

    private void onOtStageApproved(JsonNode p) {
        if (p.path("nextStage").isMissingNode() || p.path("nextStage").isNull()) return;
        Map<String, Object> vars = otVars(p);
        vars.put("stageNo", asString(p, "stageNo"));
        dispatchToNextStage(p.path("nextStage"), NotificationType.OT_STAGE_APPROVED, vars,
                "overtime.stage_approved");
    }

    private void onOtStageRejected(JsonNode p) {
        Long submitter = readLong(p, "employeeId");
        if (submitter == null) return;
        Map<String, Object> vars = otVars(p);
        vars.put("stageNo", asString(p, "stageNo"));
        notifications.dispatch(submitter, NotificationType.OT_STAGE_REJECTED, vars);
    }

    private void onOtFinalized(JsonNode p, boolean approved) {
        Long submitter = readLong(p, "employeeId");
        if (submitter == null) return;
        notifications.dispatch(submitter,
                approved ? NotificationType.OT_APPROVED : NotificationType.OT_REJECTED,
                otVars(p));
    }

    // ----- Regularization -----

    private void onRegRequested(JsonNode p) {
        Long approver = approverFromFirstStage(p);
        if (approver == null) return;
        notifications.dispatch(approver, NotificationType.REGULARIZATION_REQUESTED, regVars(p));
    }

    private void onRegStageApproved(JsonNode p) {
        if (p.path("nextStage").isMissingNode() || p.path("nextStage").isNull()) return;
        Map<String, Object> vars = regVars(p);
        vars.put("stageNo", asString(p, "stageNo"));
        dispatchToNextStage(p.path("nextStage"), NotificationType.REGULARIZATION_STAGE_APPROVED, vars,
                "regularization.stage_approved");
    }

    private void onRegStageRejected(JsonNode p) {
        Long submitter = readLong(p, "employeeId");
        if (submitter == null) return;
        Map<String, Object> vars = regVars(p);
        vars.put("stageNo", asString(p, "stageNo"));
        notifications.dispatch(submitter, NotificationType.REGULARIZATION_STAGE_REJECTED, vars);
    }

    private void onRegFinalized(JsonNode p, boolean approved) {
        Long submitter = readLong(p, "employeeId");
        if (submitter == null) return;
        notifications.dispatch(submitter,
                approved ? NotificationType.REGULARIZATION_APPROVED : NotificationType.REGULARIZATION_REJECTED,
                regVars(p));
    }

    // ----- Payroll -----

    private void onPayslipGenerated(JsonNode p) {
        Long userId = readLong(p, "userId");
        if (userId == null) {
            // Bells are keyed by userId, never employeeId — non-ESS employees
            // simply have no user to notify. The previous fallback to
            // employeeId silently delivered to the wrong user.
            log.debug("payroll.payslip.generated has no userId — skipping bell");
            return;
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("payslipId", asString(p, "payslipId"));
        vars.put("period", asString(p, "period"));
        vars.put("net", asString(p, "net"));
        notifications.dispatch(userId, NotificationType.PAYSLIP_READY, vars);
    }

    // ----- Auth -----

    private void onPasswordResetByAdmin(JsonNode p) {
        Long userId = readLong(p, "userId");
        if (userId == null) return;
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("actorName", asString(p, "byUsername"));
        notifications.dispatch(userId, NotificationType.PASSWORD_RESET_BY_ADMIN, vars);
    }

    private void onSessionRevoked(JsonNode p) {
        String reason = asString(p, "reason");
        if (reason == null || !"ADMIN_REVOKE".equalsIgnoreCase(reason)) return;
        Long userId = readLong(p, "userId");
        if (userId == null) return;
        notifications.dispatch(userId, NotificationType.SESSION_REVOKED_BY_ADMIN, Map.of());
    }

    // ----- Auth / role projection -----

    /**
     * Bug #7 — projection upsert for {@code auth.user.role_assigned}.
     * No bell row written: this only mutates the {@code role_members}
     * snapshot consulted by {@link #dispatchToNextStage} when a workflow
     * stage targets a role with no specific assignedTo.
     */
    private void onRoleAssigned(JsonNode p) {
        Long userId = readLong(p, "userId");
        String roleCode = asString(p, "roleCode");
        if (userId == null || roleCode == null || roleCode.isBlank()) return;
        roles.onGranted(roleCode, userId);
    }

    private void onRoleRevoked(JsonNode p) {
        Long userId = readLong(p, "userId");
        String roleCode = asString(p, "roleCode");
        if (userId == null || roleCode == null || roleCode.isBlank()) return;
        roles.onRevoked(roleCode, userId);
    }

    /**
     * Bug #7 — fans a stage notification to either the explicit assignee
     * or, when the stage is role-only ({@code assignedTo == null,
     * stageRole != null}), every user currently holding that role per
     * the local {@code role_members} projection. Empty fan-out (no
     * matching users) logs at warn so a misconfigured workflow stage
     * doesn't go silent indefinitely.
     */
    private void dispatchToNextStage(JsonNode nextStage, String notificationType,
                                     Map<String, Object> vars, String eventLabel) {
        Long approver = readLong(nextStage, "assignedTo");
        if (approver != null) {
            notifications.dispatch(approver, notificationType, vars);
            return;
        }
        String stageRole = asString(nextStage, "stageRole");
        if (stageRole == null || stageRole.isBlank()) {
            log.warn("{} nextStage has neither assignedTo nor stageRole — skipped", eventLabel);
            return;
        }
        java.util.List<Long> userIds = roles.usersWithRole(stageRole);
        if (userIds.isEmpty()) {
            log.warn("{} nextStage targets role {} but role_members is empty — "
                    + "bell skipped (run POST /admin/users/replay/role-grants if this is a fresh deploy)",
                    eventLabel, stageRole);
            return;
        }
        notifications.dispatchBulk(new java.util.LinkedHashSet<>(userIds), notificationType, vars);
    }

    // ----- Employee -----

    private void onManagerChanged(JsonNode p) {
        Long employeeId = readLong(p, "employeeId");
        Long oldManagerId = readLong(p, "oldManagerId");
        Long newManagerId = readLong(p, "newManagerId");

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("employeeId", asString(p, "employeeId"));
        base.put("employeeName", asString(p, "employeeName"));
        base.put("oldManagerName", asString(p, "oldManagerName"));
        base.put("newManagerName", asString(p, "newManagerName"));

        if (employeeId != null) {
            notifications.dispatch(employeeId, NotificationType.EMPLOYEE_MANAGER_CHANGED_EMPLOYEE, base);
        }
        if (oldManagerId != null) {
            notifications.dispatch(oldManagerId, NotificationType.EMPLOYEE_MANAGER_CHANGED_OLD_MANAGER, base);
        }
        if (newManagerId != null) {
            notifications.dispatch(newManagerId, NotificationType.EMPLOYEE_MANAGER_CHANGED_NEW_MANAGER, base);
        }
    }

    private void onEmployeeSuspended(JsonNode p) {
        // Suspended user is auth-disabled — notify the manager (and HR if the manager isn't the actor).
        Long managerId = readLong(p, "managerId");
        if (managerId == null) return;
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("employeeId", asString(p, "employeeId"));
        vars.put("employeeName", asString(p, "employeeName"));
        vars.put("startDate", asString(p, "startDate"));
        vars.put("endDate", asString(p, "endDate"));
        vars.put("reason", asString(p, "reason"));
        notifications.dispatch(managerId, NotificationType.EMPLOYEE_SUSPENDED, vars);
    }

    private void onSuspensionLifted(JsonNode p) {
        Long userId = readLong(p, "userId");
        Long managerId = readLong(p, "managerId");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("employeeId", asString(p, "employeeId"));
        vars.put("employeeName", asString(p, "employeeName"));
        vars.put("liftedOn", asString(p, "liftedOn"));
        if (userId != null) {
            notifications.dispatch(userId, NotificationType.EMPLOYEE_SUSPENSION_LIFTED, vars);
        } else {
            log.debug("employee.suspension_lifted has no userId — skipping employee bell");
        }
        if (managerId != null) {
            notifications.dispatch(managerId, NotificationType.EMPLOYEE_SUSPENSION_LIFTED_MANAGER, vars);
        }
    }

    // ----- Attendance -----

    private void onShiftOverridden(JsonNode p) {
        Long userId = readLong(p, "userId");
        if (userId == null) {
            log.debug("attendance.shift_overridden has no userId — skipping bell");
            return;
        }
        String op = asString(p, "op");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("fromDate", asString(p, "fromDate"));
        vars.put("toDate", asString(p, "toDate"));
        vars.put("shiftStart", asString(p, "shiftStart"));
        vars.put("shiftEnd", asString(p, "shiftEnd"));
        String type = "REVERTED".equalsIgnoreCase(op)
                ? NotificationType.SHIFT_OVERRIDE_REVERTED
                : NotificationType.SHIFT_OVERRIDDEN;
        notifications.dispatch(userId, type, vars);
    }

    // ----- Chat -----

    /**
     * Bell-drop matrix for {@code chat.message.sent}:
     * <ul>
     *   <li>DM        → bell for every recipient (always).</li>
     *   <li>GROUP     → bell only for users in {@code mentionedUserIds}.</li>
     *   <li>BROADCAST → bell only for users in {@code mentionedUserIds}.</li>
     * </ul>
     * Sender never gets a bell entry for their own message.
     */
    private void onChatMessageSent(JsonNode p) {
        String convType = asString(p, "conversationType");
        if (convType == null) return;
        Long senderUserId = readLong(p, "senderUserId");
        Long messageId = readLong(p, "messageId");
        Long conversationId = readLong(p, "conversationId");
        String senderName = asString(p, "senderUsername");
        String preview = asString(p, "preview");

        java.util.Set<Long> bellRecipients = new java.util.LinkedHashSet<>();
        switch (convType.toUpperCase()) {
            case "DM" -> {
                for (Long uid : readLongArray(p.path("recipientUserIds"))) bellRecipients.add(uid);
            }
            case "GROUP", "BROADCAST" -> {
                for (Long uid : readLongArray(p.path("mentionedUserIds"))) bellRecipients.add(uid);
            }
            default -> { }
        }
        if (senderUserId != null) bellRecipients.remove(senderUserId);
        if (bellRecipients.isEmpty()) return;

        boolean isMention = "GROUP".equalsIgnoreCase(convType) || "BROADCAST".equalsIgnoreCase(convType);
        String type = isMention ? NotificationType.CHAT_MENTION : NotificationType.CHAT_DM;
        // Resolve the senderName fallback here so the template can stay
        // a plain {{senderName}} — unknown placeholders render as "—",
        // which would read worse than "Someone" / "a colleague".
        String resolvedSender = (senderName != null && !senderName.isBlank())
                ? senderName
                : (isMention ? "Someone" : "a colleague");

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("conversationId", conversationId == null ? null : conversationId.toString());
        vars.put("messageId", messageId == null ? null : messageId.toString());
        vars.put("conversationType", convType);
        vars.put("senderName", resolvedSender);
        vars.put("preview", preview == null ? "" : preview);

        // Bulk dispatch: one template lookup + batch opt-out + batched
        // INSERT + pipelined Redis. Replaces a per-recipient loop that
        // hit ~120 round-trips for a 50-person mention.
        notifications.dispatchBulk(bellRecipients, type, vars);
    }

    private static java.util.List<Long> readLongArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return java.util.List.of();
        java.util.List<Long> out = new java.util.ArrayList<>(arr.size());
        for (JsonNode el : arr) {
            if (el.isNumber()) out.add(el.asLong());
            else if (el.isTextual() && !el.asString().isBlank()) {
                try { out.add(Long.parseLong(el.asString())); } catch (NumberFormatException ignored) { }
            }
        }
        return out;
    }

    // ----- common var builders -----

    private Map<String, Object> leaveVars(JsonNode p) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("requestId", asString(p, "requestId"));
        vars.put("employeeId", asString(p, "employeeId"));
        vars.put("employeeName", asString(p, "employeeName"));
        vars.put("fromDate", asString(p, "fromDate"));
        vars.put("toDate", asString(p, "toDate"));
        vars.put("days", asString(p, "days"));
        vars.put("approvedDays", asString(p, "approvedDays"));
        vars.put("requestedDays", asString(p, "requestedDays"));
        vars.put("reason", asString(p, "reason"));
        vars.put("actorName", asString(p, "actorName"));
        return vars;
    }

    private Map<String, Object> otVars(JsonNode p) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("requestId", asString(p, "requestId"));
        vars.put("employeeId", asString(p, "employeeId"));
        vars.put("employeeName", asString(p, "employeeName"));
        vars.put("date", asString(p, "date"));
        vars.put("hours", asString(p, "hours"));
        vars.put("reason", asString(p, "reason"));
        vars.put("actorName", asString(p, "actorName"));
        return vars;
    }

    private Map<String, Object> regVars(JsonNode p) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("requestId", asString(p, "requestId"));
        vars.put("employeeId", asString(p, "employeeId"));
        vars.put("employeeName", asString(p, "employeeName"));
        vars.put("date", asString(p, "date"));
        vars.put("reason", asString(p, "reason"));
        vars.put("actorName", asString(p, "actorName"));
        return vars;
    }

    private static Long approverFromFirstStage(JsonNode p) {
        JsonNode stages = p.path("stages");
        if (stages.isArray() && stages.size() > 0) {
            return readLong(stages.get(0), "assignedTo");
        }
        // Fallback: payload-level assignedTo.
        return readLong(p, "assignedTo");
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asString();
    }

    private static String asString(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        if (v.isTextual()) return v.asString();
        return v.toString();
    }

    private static Long readLong(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isNumber()) return v.asLong();
        if (v.isTextual() && !v.asString().isBlank()) {
            try { return Long.parseLong(v.asString()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
}
