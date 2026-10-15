package com.forward.hrms.payroll.enums;

/** How an accounts report is presented. Drives which viewer the FE opens. */
public enum AccountsReportOutput {
    LIST,
    GROUPED_LIST,
    SUMMARY,
    SLIP,
    LETTER,
    LEDGER,
    EXPORT_FILE
}
