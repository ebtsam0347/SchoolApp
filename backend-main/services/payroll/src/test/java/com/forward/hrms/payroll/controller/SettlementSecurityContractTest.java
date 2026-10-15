package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The separation of duty on employee final settlements, asserted on the
 * annotations that actually enforce it.
 *
 * <pre>
 * HR       -> create, approve, reject, cancel   (payroll.write)
 * Accounts -> pay                               (payroll.settlement.accounts)
 * </pre>
 *
 * <p>The load-bearing assertion is {@link #payIsNotGatedOnPayrollWrite()}.
 * {@code /pay} used to carry {@code payroll.write} — the same permission as
 * {@code /hr-approval-confirmation} — which 403'd ACCOUNTS (the persona
 * deliberately has no payroll write surface, auth V0026) and handed HR both
 * halves of the split. Fixed by auth V0037.
 *
 * <p>Reflection rather than MockMvc: the aspect reads exactly this
 * annotation, so the annotation is the contract, and this runs in
 * milliseconds without a Spring context.
 */
class SettlementSecurityContractTest {

    private static final String HR = "payroll.write";
    private static final String ACCOUNTS = "payroll.settlement.accounts";
    private static final String READ = "payroll.read";

    @Test
    void hrOwnsCreateApproveRejectAndCancel() {
        assertThat(permissionOn("create")).containsExactly(HR);
        assertThat(permissionOn("hrApproval")).containsExactly(HR);
        assertThat(permissionOn("reject")).containsExactly(HR);
        assertThat(permissionOn("cancel")).containsExactly(HR);
    }

    @Test
    void accountsOwnsPayment() {
        assertThat(permissionOn("markPaid")).containsExactly(ACCOUNTS);
    }

    @Test
    void payIsNotGatedOnPayrollWrite() {
        assertThat(permissionOn("markPaid"))
                .as("ACCOUNTS does not hold payroll.write (auth V0026) — gating /pay on it "
                        + "would 403 Accounts and give HR the payment authority too")
                .doesNotContain(HR);
    }

    @Test
    void approvalIsNotGatedOnTheAccountsPermission() {
        assertThat(permissionOn("hrApproval"))
                .as("HR verification is HR's call; Accounts is the payment authority, "
                        + "not an approval layer")
                .doesNotContain(ACCOUNTS);
    }

    @Test
    void cancelIsNotGatedOnTheAccountsPermission() {
        assertThat(permissionOn("cancel"))
                .as("cancellation reverses HR's closing and the termination — HR's call, "
                        + "not Accounts'")
                .doesNotContain(ACCOUNTS);
    }

    @Test
    void readsStayOnPayrollReadSoAccountsCanSeeItsQueue() {
        assertThat(permissionOn("preview")).containsExactly(READ);
        assertThat(permissionOn("SubmittedSettlement")).containsExactly(READ);
        assertThat(permissionOn("get")).containsExactly(READ);
        assertThat(permissionOn("search")).containsExactly(READ);
    }

    @Test
    void everyEndpointIsGated() {
        for (Method m : SettlementController.class.getDeclaredMethods()) {
            if (m.isSynthetic()) continue;
            assertThat(m.getAnnotation(RequirePermission.class))
                    .as("%s() has no @RequirePermission", m.getName())
                    .isNotNull();
        }
    }

    private static String[] permissionOn(String method) {
        return Arrays.stream(SettlementController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(method) && !m.isSynthetic())
                .findFirst()
                .map(m -> m.getAnnotation(RequirePermission.class))
                .map(RequirePermission::value)
                .orElseThrow(() -> new AssertionError(
                        "no @RequirePermission on SettlementController." + method + "()"));
    }
}
