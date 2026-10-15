package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The separation of duty, asserted on the annotations that actually
 * enforce it.
 *
 * <pre>
 * HR       -> create, edit, cancel   (payroll.write)
 * Accounts -> pay                    (payroll.offcycle.accounts)
 * </pre>
 *
 * <p>The load-bearing assertion is {@link #payIsNotGatedOnPayrollWrite()}.
 * ACCOUNTS deliberately does not hold {@code payroll.write} (auth V0026),
 * so gating {@code /pay} on it would 403 the only role meant to use it —
 * and simultaneously hand HR both halves of the split. The final-settlement
 * {@code /pay} endpoint had the same bug; it was fixed in auth V0037 and is
 * guarded by {@link SettlementSecurityContractTest}.
 *
 * <p>Reflection rather than MockMvc: the aspect reads exactly this
 * annotation, so the annotation is the contract, and this runs in
 * milliseconds without a Spring context.
 */
class OffCyclePaymentSecurityContractTest {

    private static final String HR = "payroll.write";
    private static final String ACCOUNTS = "payroll.offcycle.accounts";
    private static final String READ = "payroll.read";

    @Test
    void hrOwnsCreateEditAndCancel() {
        assertThat(permissionOn("create")).containsExactly(HR);
        assertThat(permissionOn("update")).containsExactly(HR);
        assertThat(permissionOn("cancel")).containsExactly(HR);
    }

    @Test
    void accountsOwnsPayment() {
        assertThat(permissionOn("pay")).containsExactly(ACCOUNTS);
    }

    @Test
    void payIsNotGatedOnPayrollWrite() {
        assertThat(permissionOn("pay"))
                .as("ACCOUNTS does not hold payroll.write (auth V0026) — gating /pay on it "
                        + "would 403 Accounts and give HR the payment authority too")
                .doesNotContain(HR);
    }

    @Test
    void cancelIsNotGatedOnTheAccountsPermission() {
        assertThat(permissionOn("cancel"))
                .as("cancellation is HR's call; Accounts is the payment authority, "
                        + "not a second approval layer")
                .doesNotContain(ACCOUNTS);
    }

    @Test
    void readsStayOnPayrollReadSoAccountsCanSeeItsQueue() {
        assertThat(permissionOn("list")).containsExactly(READ);
        assertThat(permissionOn("get")).containsExactly(READ);
    }

    @Test
    void everyMutatingEndpointIsGated() {
        for (Method m : OffCyclePaymentController.class.getDeclaredMethods()) {
            if (m.isSynthetic()) continue;
            assertThat(m.getAnnotation(RequirePermission.class))
                    .as("%s() has no @RequirePermission", m.getName())
                    .isNotNull();
        }
    }

    private static String[] permissionOn(String method) {
        return Arrays.stream(OffCyclePaymentController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(method) && !m.isSynthetic())
                .findFirst()
                .map(m -> m.getAnnotation(RequirePermission.class))
                .map(RequirePermission::value)
                .orElseThrow(() -> new AssertionError(
                        "no @RequirePermission on OffCyclePaymentController." + method + "()"));
    }
}
