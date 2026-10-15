package com.forward.hrms.payroll.events;

public final class AnnualLeaveEvents {

    public static final String TOPIC = "annual-leave";

    public static final String ANNUAL_LEAVE_CALCULATED =
            "annual_leave_calculated";


    public record Calculated(
            Integer year,
            Integer month,
            int totalEmployees,
            int successCount,
            int failedCount
    ) {
    }
}
