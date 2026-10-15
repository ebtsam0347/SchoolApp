package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowanceTransactionDto {
    private Long employeeId;
    private String employeeCode;
    private EmployeeSummary employee;
    private String grade;
    private String fiscalYearCode;
    private Integer allowanceYear;
    private Integer allowanceMonth;
    private List<ComponentDto> components;
}