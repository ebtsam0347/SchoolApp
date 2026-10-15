package com.forward.hrms.payroll.enums;

/**
 * How a salary component's amount is interpreted at compute time.
 * <ul>
 *   <li>{@link #FLAT} — the amount is the monthly figure. Subject to LOP
 *       proration on EARNING lines; flat as-is on DEDUCTION lines.</li>
 *   <li>{@link #PER_DAY} — the amount is a daily rate, multiplied by
 *       {@code days_present} from attendance. Restricted to EARNING.</li>
 * </ul>
 */
public enum CalcType {
    FLAT,
    PER_DAY
}
