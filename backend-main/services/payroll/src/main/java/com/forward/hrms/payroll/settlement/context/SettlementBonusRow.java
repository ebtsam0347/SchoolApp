package com.forward.hrms.payroll.settlement.context;


import com.forward.hrms.payroll.entity.BonusEvent;
import com.forward.hrms.payroll.entity.BonusEventAmount;

public record SettlementBonusRow(
        BonusEvent event,
        BonusEventAmount amount
) {
}
