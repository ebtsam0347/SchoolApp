package com.forward.hrms.payroll.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.dto.request.CreateBonusNoSettingsRequest;
import com.forward.hrms.payroll.dto.request.UpdateBonusNoSettingsRequest;
import com.forward.hrms.payroll.dto.response.*;
import com.forward.hrms.payroll.entity.BonusNoSettings;
import com.forward.hrms.payroll.entity.BonusTransaction;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.payroll.enums.BonusNoSettingsStatus;
import com.forward.hrms.payroll.enums.BonusTransactionPaymentMode;
import com.forward.hrms.payroll.enums.BonusTransactionStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static java.util.stream.Collectors.toList;

@Service
public class BonusNoSettingsService {

    private final BonusNoSettingsRepository bonusNoSettingsRepository;
    private final BonusTransactionRepository bonusTransactionRepository;
    private final FiscalYearRepository fiscalYearRepository;
    private final SagaService sagaService;
    private final ObjectMapper sagaJson;
    private final AuditEmitter audit;
    private final EmployeeDisplayCache displayCache;
    private final AllowanceTransactionRepository allowanceTransactionRepository;

    public BonusNoSettingsService(
            BonusNoSettingsRepository bonusNoSettingsRepository,
            BonusTransactionRepository bonusTransactionRepository,
            FiscalYearRepository fiscalYearRepository,
            SagaService sagaService,
            @Qualifier("sagaObjectMapper") ObjectMapper sagaJson,
            AuditEmitter audit, EmployeeDisplayCache displayCache,
            AllowanceTransactionRepository allowanceTransactionRepository) {
        this.bonusNoSettingsRepository = bonusNoSettingsRepository;
        this.bonusTransactionRepository = bonusTransactionRepository;
        this.fiscalYearRepository = fiscalYearRepository;
        this.sagaService = sagaService;
        this.sagaJson = sagaJson;
        this.audit = audit;
        this.displayCache = displayCache;
        this.allowanceTransactionRepository = allowanceTransactionRepository;
    }

    @Transactional
    public BonusNoSettingsDto create(CreateBonusNoSettingsRequest req) {
        if (req.fiscalYearCode() == null || req.fiscalYearCode().isBlank()) {
            throw new ConflictError(PayrollErrorCode.INVALID_PERIOD, "fiscalYearCode is required");
        }
        if (req.bonusNo() == null || req.bonusNo() < 1 || req.bonusNo() > 4) {
            throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_INVALID_STATE, "bonusNo must be between 1 and 4");
        }
        if (req.percentageOfSalary() == null
                || req.percentageOfSalary().signum() < 0
                || req.percentageOfSalary().equals(new java.math.BigDecimal("0"))
                || req.percentageOfSalary().compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_INVALID_STATE,
                    "percentageOfSalary must be between 1 and 100");
        }
        if (!fiscalYearRepository.existsById(req.fiscalYearCode())) {
            throw new ConflictError(PayrollErrorCode.FISCAL_YEAR_NOT_FOUND,
                    "fiscal year " + req.fiscalYearCode() + " not found");
        }
        if (bonusNoSettingsRepository.existsByFiscalYearCodeAndBonusNo(req.fiscalYearCode(), req.bonusNo())) {
            throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_DUPLICATE,
                    "bonus settings already exists for fiscal year " + req.fiscalYearCode()
                            + " and bonus_no " + req.bonusNo());
        }
        BonusNoSettings row = new BonusNoSettings();
        row.setFiscalYearCode(req.fiscalYearCode());
        row.setBonusNo(req.bonusNo());
        row.setPercentageOfSalary(req.percentageOfSalary());
        row.setStatus(BonusNoSettingsStatus.DRAFT);
        row = bonusNoSettingsRepository.save(row);

        audit.emit("bonus_no_settings", String.valueOf(row.getId()), "created", null,
                Map.of(
                        "fiscalYearCode", row.getFiscalYearCode(),
                        "bonusNo", row.getBonusNo(),
                        "percentageOfSalary", row.getPercentageOfSalary(),
                        "status", row.getStatus()),
                true);
        return BonusNoSettingsDtoMapper.of(row);
    }

    @Transactional(readOnly = true)
    public BonusNoSettingsDto get(long id) {
        return BonusNoSettingsDtoMapper.of(load(id));
    }

    @Transactional(readOnly = true)
    public List<BonusNoSettingsDto> list(String fiscalYearCode, BonusNoSettingsStatus status) {
        return bonusNoSettingsRepository.filter(trimToNull(fiscalYearCode), status).stream()
                .map(BonusNoSettingsDtoMapper::of)
                .toList();
    }

    @Transactional
    public BonusNoSettingsDto update(long id, UpdateBonusNoSettingsRequest req) {
        BonusNoSettings row = load(id);
        if(row.getStatus() != BonusNoSettingsStatus.DRAFT){
            throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_INVALID_STATE,
                    "update requires DRAFT staus of bonus settings. Current is " + row.getStatus());
        }
        int transactions = bonusTransactionRepository.countByFiscalYearCodeAndBonusNo(
                row.getFiscalYearCode(), row.getBonusNo());
        if (transactions > 0) {
            throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_INVALID_STATE,
                    "bonus no settings cannot be change since bonus is already calculated.");
        }
        if (req.percentageOfSalary() != null) {
            if (req.percentageOfSalary().signum() < 0 || req.percentageOfSalary().signum() == 0
                    || req.percentageOfSalary().compareTo(new java.math.BigDecimal("100")) > 0) {
                throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_INVALID_STATE,
                        "percentageOfSalary must be between 1 and 100");
            }
            row.setPercentageOfSalary(req.percentageOfSalary());
        }
        audit.emit("bonus_no_settings", String.valueOf(row.getId()), "updated", null,
                Map.of(
                        "fiscalYearCode", row.getFiscalYearCode(),
                        "bonusNo", row.getBonusNo(),
                        "percentageOfSalary", row.getPercentageOfSalary(),
                        "status", row.getStatus()),
                true);
        return BonusNoSettingsDtoMapper.of(row);
    }

    @Transactional
    public void delete(long id) {
        BonusNoSettings row = load(id);
        int transactions = bonusTransactionRepository.countByFiscalYearCodeAndBonusNo(
                row.getFiscalYearCode(), row.getBonusNo());
        if (transactions > 0) {
            throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_INVALID_STATE,
                    "cannot delete bonus settings with existing bonus transactions");
        }
        bonusNoSettingsRepository.delete(row);
        audit.emit("bonus_no_settings", String.valueOf(id), "deleted", null,
                Map.of("fiscalYearCode", row.getFiscalYearCode(), "bonusNo", row.getBonusNo()), true);
    }

    @Transactional
    public BonusNoSettingsDto compute(long id) {
        BonusNoSettings row = load(id);
        if (row.getStatus() == BonusNoSettingsStatus.COMPUTING) {
            startComputeSaga(row);
            return BonusNoSettingsDtoMapper.of(row);
        }
        if (row.getStatus() != BonusNoSettingsStatus.DRAFT) {
            throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_INVALID_STATE,
                    "compute requires DRAFT status of bonus settings. Current is " + row.getStatus());
        }

        row.setStatus(BonusNoSettingsStatus.COMPUTING);
        startComputeSaga(row);
        return BonusNoSettingsDtoMapper.of(row);
    }

    @Transactional
    public BonusNoSettingsDto reset(long id) {
        BonusNoSettings row = load(id);
        if(row.getStatus() != BonusNoSettingsStatus.COMPUTED){
            throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_INVALID_STATE,
                    "reset requires COMPUTED status of bonus settings. Current is " + row.getStatus());
        }
        int deletedTransaction = bonusTransactionRepository.deleteAllByFiscalYearCodeAndBonusNo(
                row.getFiscalYearCode(), row.getBonusNo());
        int droppedSaga = sagaService.clear(BonusTransactionComputeSaga.NAME, "bonus_no_settings:fy:" + row.getFiscalYearCode() +
                ":bonus_no:" + row.getBonusNo());
        row.setStatus(BonusNoSettingsStatus.DRAFT);
        audit.emit("bonus_no_settings", String.valueOf(row.getId()), "reset", null,
                Map.of(
                        "deletedTransactions", deletedTransaction,
                        "droppedSaga", droppedSaga),
                true);
        return BonusNoSettingsDtoMapper.of(row);
    }
    @Transactional
    public BonusNoSettingsDto approve(long id){
        BonusNoSettings row = load(id);
        if(row.getStatus() != BonusNoSettingsStatus.COMPUTED){
            throw new ConflictError(PayrollErrorCode.BONUS_NO_SETTINGS_INVALID_STATE,
                    "approve required computed status of bonus settings. Current is: " + row.getStatus());
        }
        row.setStatus(BonusNoSettingsStatus.APPROVED);
        BonusNoSettings approved = bonusNoSettingsRepository.save(row);
        bonusTransactionRepository.approveAllBonusTransactionByFiscalYearAndBonusNo(row.getFiscalYearCode(), row.getBonusNo(),
                BonusTransactionStatus.PAID,
                LocalDateTime.now());
        audit.emit("bonus_no_setting", String.valueOf(row.getId()), "approved", row, approved, true);
        audit.emit("bonus_transaction", "fy:" + row.getFiscalYearCode() + "bonus_no:" + row.getBonusNo(), "approved", null,
                Map.of("fiscalYearCode", row.getFiscalYearCode(), "bonusNo", row.getBonusNo()), true);
        return BonusNoSettingsDtoMapper.of(approved);
    }

    @Transactional(readOnly = true)
    public PageResponse<BonusTransactionDto> transactions(long id, BonusTransactionStatus status,
                                                          BonusTransactionPaymentMode paymentMode, String employeeCode,
                                                          Pageable pageable) {
        BonusNoSettings row = load(id);
        String query = trimToNull(employeeCode);
        Page<BonusTransaction> allTransactions = bonusTransactionRepository.search(row.getFiscalYearCode(), row.getBonusNo(), status,
                paymentMode, query, pageable);

        List<Long> employeeIds = allTransactions.stream()
                .map(BonusTransaction::getEmployeeId)
                .toList();

        Map<Long, EmployeeSummary> employees = resolveEmployees(employeeIds);

        Page<BonusTransactionDto> transactions = allTransactions.map(
                t -> BonusTransactionDtoMapper.of(t, employees.get(t.getEmployeeId()))
        );
        return PageResponse.from(transactions);
    }

    private void startComputeSaga(BonusNoSettings row) {
        ObjectNode payload = sagaJson.createObjectNode();
        payload.put("bonusNoSettingsId", row.getId());
        payload.put("fiscalYearCode", row.getFiscalYearCode());
        payload.put("bonusNo", row.getBonusNo());
        SagaContext.writeIdentity(payload, EmployeeClient.currentRequestIdentity());
        sagaService.start(BonusTransactionComputeSaga.NAME, "bonus_no_settings:fy:" + row.getFiscalYearCode() +
                ":bonus_no:" + row.getBonusNo(), payload);
    }

    private BonusNoSettings load(long id) {
        return bonusNoSettingsRepository.findById(id).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.BONUS_NO_SETTINGS_NOT_FOUND, "bonus settings not found"));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<Long, EmployeeSummary> resolveEmployees(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Set<Long> distinct = new LinkedHashSet<>(ids);
        Map<Long, EmployeeDisplay> hits = displayCache.getMany(distinct);
        Map<Long, EmployeeSummary> out = new HashMap<>(hits.size());
        for (var en : hits.entrySet()) out.put(en.getKey(), EmployeeSummaryMapper.toSummary(en.getValue()));
        return out;
    }
}
