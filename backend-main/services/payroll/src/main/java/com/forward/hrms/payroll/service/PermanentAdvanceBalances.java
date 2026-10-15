package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.entity.PermanentAdvance;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-advance view of an employee's permanent-advance pool. Deductions carry
 * no advance id — the balance is employee-level — so where a screen or the
 * settlement wants a figure per advance, the employee's total deductions are
 * applied to their APPROVED advances oldest-first.
 */
public final class PermanentAdvanceBalances {

    /** Oldest first: receiving date (unknown last), then id. */
    public static final Comparator<PermanentAdvance> OLDEST_FIRST = Comparator
            .comparing(PermanentAdvance::getReceivingDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PermanentAdvance::getId);

    private PermanentAdvanceBalances() {}

    /**
     * Amount recovered per advance id. {@code approved} is every APPROVED
     * advance of one employee (settled ones included — their deductions are
     * in {@code totalDeducted}); order doesn't matter.
     */
    public static Map<Long, BigDecimal> recoveredPerAdvance(List<PermanentAdvance> approved,
                                                            BigDecimal totalDeducted) {
        Map<Long, BigDecimal> out = new HashMap<>();
        BigDecimal left = totalDeducted == null ? BigDecimal.ZERO : totalDeducted;
        for (PermanentAdvance a : approved.stream().sorted(OLDEST_FIRST).toList()) {
            BigDecimal recovered = left.max(BigDecimal.ZERO).min(a.getAmount());
            out.put(a.getId(), recovered);
            left = left.subtract(recovered);
        }
        return out;
    }
}
