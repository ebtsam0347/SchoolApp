package com.forward.hrms.employee.enums;

import com.forward.hrms.commons.core.error.ErrorCode;

public enum EmployeeErrorCode implements ErrorCode {
    EMPLOYEE_NOT_FOUND,
    EMPLOYEE_CODE_TAKEN,
    EMPLOYEE_USER_LINK_TAKEN,
    EMPLOYEE_ALREADY_HAS_ESS,
    EMPLOYEE_NO_ESS,
    EMPLOYEE_HAS_ACTIVE_REPORTS,
    EMPLOYEE_NOT_ACTIVE,
    EMPLOYEE_TERMINATED,
    EMPLOYEE_ON_NOTICE,
    EMPLOYEE_SUSPENDED,
    MANAGER_CYCLE,
    MANAGER_NOT_FOUND,
    SECTION_DEPARTMENT_MISMATCH,
    CONTRACT_NOT_PROBATION,
    CONTRACT_NOT_FOUND,
    PROBATION_ALREADY_EXTENDED,
    PROBATION_DAYS_INVALID,
    EXIT_NOT_FOUND,
    EXIT_NOT_PENDING,
    EXIT_LAST_WORKING_DAY_INVALID,
    SUSPENSION_NOT_ACTIVE,
    SUSPENSION_ALREADY_ACTIVE,
    DISCIPLINARY_NOT_FOUND,
    DISCIPLINARY_ALREADY_DECIDED,
    PROVINCE_NOT_FOUND,
    PROVINCE_CODE_TAKEN,
    CITY_NOT_FOUND,
    CITY_CODE_TAKEN,
    DEPARTMENT_NOT_FOUND,
    DEPARTMENT_CODE_TAKEN,
    SECTION_NOT_FOUND,
    SECTION_CODE_TAKEN,
    DESIGNATION_NOT_FOUND,
    DESIGNATION_CODE_TAKEN,
    GRADE_NOT_FOUND,
    GRADE_CODE_TAKEN,
    SKILL_NOT_FOUND,
    SKILL_CODE_TAKEN,
    QUALIFICATION_NOT_FOUND,
    QUALIFICATION_CODE_TAKEN,
    CATALOG_INACTIVE,
    DOCUMENT_NOT_FOUND,
    EMERGENCY_CONTACT_NOT_FOUND,
    DISABILITY_NOT_FOUND,
    DISABILITY_CODE_TAKEN,
    EMPLOYEE_DISABILITY_NOT_FOUND,
    EMPLOYEE_DISABILITY_DUPLICATE,
    EMPLOYEE_SKILL_NOT_FOUND,
    EMPLOYEE_QUALIFICATION_NOT_FOUND,
    PRIOR_EXPERIENCE_NOT_FOUND,
    SAGA_NOT_FOUND,
    UPSTREAM_AUTH_FAILED,
    /** §7.employee — per-year sequence overflow (>9999). */
    EMPLOYEE_CODE_EXHAUSTED,
    /** §7.employee — changeManager target is TERMINATED. */
    MANAGER_TERMINATED,
    /** §7.employee #13/#25 — generic 422 for OnboardingService.validate /
     *  EmployeeService.update where the prior code was the misnomer
     *  EMPLOYEE_NOT_FOUND. */
    VALIDATION_FAILED,
    APPOINTMENT_LETTER_DATA_MISSING,
    /** §7.employee #2 — provisionEss=true with no email or workEmail. */
    ESS_EMAIL_REQUIRED,
    /** §7.employee #11 — duplicate emergency contact in the request. */
    EMERGENCY_CONTACT_DUPLICATE,
    /** §7.employee #14 — duplicate qualification in the request. */
    QUALIFICATION_DUPLICATE,
    /** §7.employee #16 — joiningDate outside the accepted business window. */
    JOINING_DATE_OUT_OF_RANGE,
    /** §7.employee #29 — lastWorkingDay too far in the future. */
    LAST_WORKING_DAY_OUT_OF_RANGE,
    /** §7.employee #34/#35 — suspension boundary date in the future. */
    SUSPENSION_DATE_INVALID,
    /** §7.employee #53 — disciplinary issuedOn in the future. */
    DISCIPLINARY_DATE_INVALID,
    /** §7.employee #66 — list filter mutex (probation due-within vs overdue). */
    LIST_FILTER_CONFLICT,
    /** §7.employee #15 — employee under 18 at joining date. */
    MIN_AGE_NOT_MET,
    /** §7.employee #41 — uploaded document exceeds the configured cap. */
    DOCUMENT_TOO_LARGE,
    /** Duplicate skill in an onboarding request, or skill already linked to the employee. */
    EMPLOYEE_SKILL_DUPLICATE,
    /** §7.employee #7 — religion not found. */
    RELIGION_NOT_FOUND,
    RELIGION_NAME_TAKEN,

    OFF_CYCLE_PAYMENT_TYPE_NOT_FOUND,
    OFF_CYCLE_PAYMENT_TYPE_NAME_TAKEN,

    REQUISITION_NOT_FOUND,
    REQUISITION_INVALID_STATE,

    BELONGING_MASTER_NOT_FOUND,
    BELONGING_ISSUANCE_NOT_FOUND,

    TOUR_NOT_FOUND,
    TOUR_INVALID_STATE,
    TOUR_VALIDATION_FAILED,
    /** Per-year factory_tour_sequence overflow (>9999). */
    TOUR_CODE_EXHAUSTED,

    /** /approvals/history — missing dates, inverted range, or window over the cap. */
    APPROVAL_HISTORY_DATE_RANGE_INVALID
}
