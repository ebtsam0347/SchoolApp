package com.forward.hrms.employee.enums;

public enum HistoryChangeReason {
    ONBOARD,
    TRANSFER,
    PROMOTION,
    DEMOTION,
    DEPARTMENT_CHANGE,
    MANAGER_CHANGE,
    SECTION_CHANGE,
    EXIT,
    /** History row reopened by TerminationSaga.undoTermination() after HR rejects a final settlement. */
    REINSTATED
}
