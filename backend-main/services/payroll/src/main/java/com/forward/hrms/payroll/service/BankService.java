package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.dto.request.CreateBankRequest;
import com.forward.hrms.payroll.dto.request.UpdateBankRequest;
import com.forward.hrms.payroll.dto.response.BankDto;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.BankRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankService {

    private final BankRepository repo;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<BankDto> list(boolean includeInactive) {
        List<Bank> rows = includeInactive
                ? repo.findAllByOrderByCodeAsc()
                : repo.findAllByActiveTrueOrderByCodeAsc();
        return rows.stream().map(BankDto::of).toList();
    }

    @Transactional(readOnly = true)
    public BankDto get(long id) {
        return BankDto.of(load(id));
    }

    @Transactional
    public BankDto create(CreateBankRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repo.existsByCode(code)) {
            throw new ConflictError(PayrollErrorCode.BANK_CODE_TAKEN,
                    "bank code already exists: " + code);
        }
        Bank b = new Bank();
        b.setCode(code);
        b.setName(req.name().trim());
        b.setBranch(req.branch());
        b.setAddress(req.address());
        b.setAccountTitle(req.accountTitle());
        b.setIban(req.iban());
        b.setAccountNumber(req.accountNumber());
        b.setActive(true);
        b = repo.save(b);
        audit.emit("bank", String.valueOf(b.getId()), "created", null, snapshot(b), false);
        return BankDto.of(b);
    }

    @Transactional
    public BankDto update(long id, UpdateBankRequest req) {
        Bank b = load(id);
        Map<String, Object> before = snapshot(b);
        if (req.name() != null && !req.name().isBlank()) b.setName(req.name().trim());
        if (req.branch() != null) b.setBranch(req.branch().isBlank() ? null : req.branch());
        if (req.address() != null) b.setAddress(req.address().isBlank() ? null : req.address());
        if (req.accountTitle() != null) b.setAccountTitle(req.accountTitle().isBlank() ? null : req.accountTitle());
        if (req.iban() != null) b.setIban(req.iban().isBlank() ? null : req.iban());
        if (req.accountNumber() != null) b.setAccountNumber(req.accountNumber().isBlank() ? null : req.accountNumber());
        if (req.active() != null) b.setActive(req.active());
        audit.emit("bank", String.valueOf(b.getId()), "updated", before, snapshot(b), false);
        return BankDto.of(b);
    }

    @Transactional
    public BankDto deactivate(long id) {
        Bank b = load(id);
        Map<String, Object> before = snapshot(b);
        b.setActive(false);
        audit.emit("bank", String.valueOf(b.getId()), "deactivated", before, snapshot(b), false);
        return BankDto.of(b);
    }

    private static Map<String, Object> snapshot(Bank b) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", b.getCode());
        m.put("name", b.getName());
        m.put("active", b.isActive());
        return m;
    }

    @Transactional(readOnly = true)
    public Bank requireActive(long id) {
        Bank b = load(id);
        if (!b.isActive()) {
            throw new ConflictError(PayrollErrorCode.BANK_INACTIVE, "bank is inactive");
        }
        return b;
    }

    private Bank load(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.BANK_NOT_FOUND, "bank not found"));
    }
}
