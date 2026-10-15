package com.forward.hrms.payroll.client.dto.attendace;


import java.time.LocalDate;

public record TerminationAttendanceSummaryDto(

        Long employeeId,

        LocalDate from,

        LocalDate to,

        AttendanceSummaryDto attendance,

        // Normal overtime only — Extra/Special overtime are excluded from
        // the termination settlement snapshot on purpose.
        NormalOvertimeSummaryDto normalOvertime

) {
}