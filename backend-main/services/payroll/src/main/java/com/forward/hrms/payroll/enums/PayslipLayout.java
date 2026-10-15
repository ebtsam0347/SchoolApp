package com.forward.hrms.payroll.enums;

import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;

import java.util.List;
import java.util.Locale;

/** Bulk payslip PDF layout: one slip per A4 page, or three per page with cut lines. */
public enum PayslipLayout {
    FULL, SHEET;

    /** Query-param form, {@code full} / {@code sheet}, any case. */
    public static PayslipLayout parse(String layout) {
        try {
            return valueOf(layout.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationError(PayrollErrorCode.REPORT_PARAM_INVALID, "unsupported layout",
                    List.of(new Violation("layout", "INVALID", "full or sheet")));
        }
    }
}
