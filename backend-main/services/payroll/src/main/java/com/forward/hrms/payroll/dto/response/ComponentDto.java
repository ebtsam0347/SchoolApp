package com.forward.hrms.payroll.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentDto {
    private Long componentId;
    private String code;
    private String name;
    private BigDecimal amount;
    private String status;
}