package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.payroll.dto.request.UpsertBankDetailsRequest;
import com.forward.hrms.payroll.dto.response.EmployeeBankDetailsDto;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.dto.response.EmployeeBankDetailsDtoMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmployeeBankDetailsService {

    private final EmployeeBankDetailsRepository repo;
    private final EmployeePayrollSettingsService settings;
    private final BankRepository banks;
    private final BankService bankService;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public EmployeeBankDetailsDto get(long employeeId) {
        EmployeeBankDetails b = load(employeeId);
        return EmployeeBankDetailsDtoMapper.of(b, banks.findById(b.getBankId()).orElse(null));
    }

    /**
     * Bulk lookup for a page of employees (legacy payslip's bank branch /
     * account fields). Employees with no bank details on file (e.g.
     * CASH payment mode) are simply absent from the map.
     */
    @Transactional(readOnly = true)
    public Map<Long, EmployeeBankDetailsDto> getMany(java.util.Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) return Map.of();
        java.util.List<EmployeeBankDetails> rows = repo.findByEmployeeIdIn(employeeIds);
        if (rows.isEmpty()) return Map.of();
        java.util.Set<Long> bankIds = rows.stream().map(EmployeeBankDetails::getBankId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, Bank> bankById = banks.findAllById(bankIds).stream()
                .collect(java.util.stream.Collectors.toMap(Bank::getId, b -> b));
        Map<Long, EmployeeBankDetailsDto> out = new java.util.HashMap<>();
        for (EmployeeBankDetails b : rows) {
            out.put(b.getEmployeeId(), EmployeeBankDetailsDtoMapper.of(b, bankById.get(b.getBankId())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public EmployeeBankDetails load(long employeeId) {
        return repo.findByEmployeeId(employeeId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.BANK_DETAILS_NOT_FOUND,
                        "no bank details for employee " + employeeId));
    }

    @Transactional
    public EmployeeBankDetailsDto upsert(long employeeId, UpsertBankDetailsRequest req) {
        Bank bank = bankService.requireActive(req.bankId());
        EmployeeBankDetails b = repo.findByEmployeeId(employeeId).orElseGet(() -> {
            EmployeeBankDetails fresh = new EmployeeBankDetails();
            fresh.setEmployeeId(employeeId);
            return fresh;
        });
        boolean isNew = b.getId() == null;
        b.setBankId(req.bankId());
        b.setBranch(req.branch());
        b.setAccountTitle(req.accountTitle());
        b.setIban(req.iban());
        b.setAccountNumber(req.accountNumber());
        b = repo.save(b);

        String action = isNew ? "SET" : "UPDATED";
        outbox.publish(PayrollEvents.TOPIC, "employee:" + employeeId,
                PayrollEvents.BANK_DETAILS_CHANGED,
                new PayrollEvents.BankDetailsChanged(employeeId, action,
                        CurrentUserHolder.usernameOrSystem()));
        audit.emit("employee_bank_details", String.valueOf(employeeId),
                "bank_details_" + action.toLowerCase(),
                null, Map.of("bankId", String.valueOf(req.bankId()), "iban", req.iban()), true);
        return EmployeeBankDetailsDtoMapper.of(b, bank);
    }

    @Transactional
    public void remove(long employeeId) {
        EmployeePayrollSettings s = settings.load(employeeId);
        if (s.getPaymentMode() != PaymentMode.CASH) {
            throw new ConflictError(PayrollErrorCode.BANK_DETAILS_NOT_REMOVABLE,
                    "bank details can only be removed when payment_mode=CASH");
        }
        EmployeeBankDetails b = load(employeeId);
        repo.delete(b);
        outbox.publish(PayrollEvents.TOPIC, "employee:" + employeeId,
                PayrollEvents.BANK_DETAILS_CHANGED,
                new PayrollEvents.BankDetailsChanged(employeeId, "REMOVED",
                        CurrentUserHolder.usernameOrSystem()));
        audit.emit("employee_bank_details", String.valueOf(employeeId),
                "bank_details_removed", null, null, true);
    }
}
