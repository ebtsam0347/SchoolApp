package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.payroll.dto.request.CreatePayrollConfigRequest;
import com.forward.hrms.payroll.dto.response.PayrollConfigDto;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.PayrollConfigRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollConfigService {

    private final PayrollConfigRepository repo;

    @Transactional(readOnly = true)
    public PayrollConfigDto current() {
        return PayrollConfigDto.of(effectiveOn(LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public List<PayrollConfigDto> history() {
        return repo.findAllByOrderByEffectiveFromDesc().stream()
                .map(PayrollConfigDto::of).toList();
    }

    @Transactional(readOnly = true)
    public PayrollConfig effectiveOn(LocalDate asOf) {
        return repo.findEffectiveOnDate(asOf).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.PAYROLL_CONFIG_NOT_FOUND,
                        "no payroll config effective on " + asOf));
    }

    @Transactional
    public PayrollConfigDto create(CreatePayrollConfigRequest req) {
        // Close the previous open-ended row, if any.
        repo.findEffectiveOnDate(req.effectiveFrom().minusDays(1))
                .filter(s -> s.getEffectiveTo() == null)
                .ifPresent(s -> s.setEffectiveTo(req.effectiveFrom().minusDays(1)));

        PayrollConfig s = new PayrollConfig();
        s.setEffectiveFrom(req.effectiveFrom());
        s.setEobiAmount(req.eobiAmount());
        s.setPessiRatePercent(req.pessiRatePercent());
        s.setPessiSalaryCap(req.pessiSalaryCap());
        if (req.gratuityMinServiceMonths() != null)
            s.setGratuityMinServiceMonths(req.gratuityMinServiceMonths());
        if (req.gratuityPartialYearThresholdMonths() != null)
            s.setGratuityPartialYearThresholdMonths(req.gratuityPartialYearThresholdMonths());
        if (req.canteenPerDayRateManagement() != null)
            s.setCanteenPerDayRateManagement(req.canteenPerDayRateManagement());
        if (req.canteenPerDayRateOther() != null)
            s.setCanteenPerDayRateOther(req.canteenPerDayRateOther());
        s.setNotes(req.notes());
        return PayrollConfigDto.of(repo.save(s));
    }
}
