package com.forward.hrms.employee.enums;

/** Attribute the Department Wise report breaks each department down by. */
public enum DepartmentWiseGroupBy {
    GRADE("Grade"),
    EMPLOYMENT_TYPE("Employment Type"),
    CATEGORY("Employee Category"),
    GENDER("Gender"),
    MARITAL_STATUS("Marital Status"),
    RELIGION("Religion"),
    BLOOD_GROUP("Blood Group"),
    QUALIFICATION("Qualification"),
    AGE_BAND("Age"),
    SERVICE_BAND("Length of Service");

    private final String label;

    DepartmentWiseGroupBy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
