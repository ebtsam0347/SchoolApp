package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.payroll.dto.response.FinalSettlementDetailsDto;
import com.forward.hrms.payroll.entity.*;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class FinalSettlementQueryService {

    private final EmployeeFinalSettlementRepository settlementRepository;
    private final EmployeeFinalSettlementLeaveDetailRepository leaveRepository;
    private final EmployeeFinalSettlementBonusDetailRepository bonusRepository;
    private final EmployeeFinalSettlementDeductionRepository deductionRepository;
    private final EmployeeFinalSettlementAdvanceDetailRepository advanceRepository;
    private final EmployeeFinalSettlementAttendanceRepository attendanceRepository;
    private final EmployeeFinalSettlementOvertimeRepository overtimeRepository;

    @Transactional(readOnly = true)
    public FinalSettlementDetailsDto get(Long settlementId) {

        EmployeeFinalSettlement settlement = settlementRepository.findById(settlementId)
                        .orElseThrow(() ->
                                new NotFoundError(
                                        PayrollErrorCode.FINAL_SETTLEMENT_NOT_FOUND,
                                        "Final Settlement not found : "
                                                + settlementId));

        List<EmployeeFinalSettlementLeaveDetail> leaves = leaveRepository.findBySettlementId(settlementId);
        List<EmployeeFinalSettlementBonusDetail> bonuses = bonusRepository.findBySettlementId(settlementId);
        List<EmployeeFinalSettlementDeduction> deductions = deductionRepository.findBySettlementId(settlementId);
        List<EmployeeFinalSettlementAdvanceDetail> advances = advanceRepository.findBySettlementId(settlementId);
        List<EmployeeFinalSettlementAttendance> attendanceSnapshots = attendanceRepository.findBySettlementId(settlementId);
        List<EmployeeFinalSettlementOvertime> overtimeSnapshots = overtimeRepository.findBySettlementId(settlementId);

        return build(
                settlement,
                leaves,
                bonuses,
                deductions,
                advances,
                attendanceSnapshots,
                overtimeSnapshots
        );
    }

    private FinalSettlementDetailsDto build(

            EmployeeFinalSettlement settlement,

            List<EmployeeFinalSettlementLeaveDetail> leaves,

            List<EmployeeFinalSettlementBonusDetail> bonuses,

            List<EmployeeFinalSettlementDeduction> deductions,

            List<EmployeeFinalSettlementAdvanceDetail> advances,

            List<EmployeeFinalSettlementAttendance> attendanceSnapshots,

            List<EmployeeFinalSettlementOvertime> overtimeSnapshots
    ) {

        EmployeeFinalSettlementLeaveDetail annual = leaves.stream()
                .filter(l -> "ANNUAL".equals(l.getLeaveCode()))
                .findFirst()
                .orElse(null);

        EmployeeFinalSettlementLeaveDetail medicalCasual = leaves.stream()
                .filter(l -> "MEDICAL_CASUAL".equals(l.getLeaveCode()))
                .findFirst()
                .orElse(null);

        return new FinalSettlementDetailsDto(

                settlement.getId(),

                settlement.getStatus(),

                settlement.getLastWorkingDay(),

                settlement.getRemarks(),

                mapEmployee(settlement),

                mapEarnedSalary(settlement),

                mapGratuity(settlement),

                mapAdvances(settlement),

                mapPermanentAdvances(advances),

                mapBonus(settlement, bonuses),

                mapAnnualLeave(annual),

                mapMedicalCasualLeave(medicalCasual),

                new FinalSettlementDetailsDto.ExcessiveLeaveSummary(
                        settlement.getAnnualExcessiveLeaveDays(),
                        settlement.getAnnualExcessiveLeaveAmount()
                ),

                new FinalSettlementDetailsDto.ExcessiveLeaveSummary(
                        settlement.getMedicalCasualExcessiveLeaveDays(),
                        settlement.getMedicalCasualExcessiveLeaveAmount()
                ),

                mapFairPriceShop(settlement),

                mapCanteen(settlement),

                mapDeductions(deductions),

                mapAttendanceDetail(attendanceSnapshots),

                mapOvertimeDetail(overtimeSnapshots),

                mapTotals(settlement),

                mapAudit(settlement)
        );
    }


    private FinalSettlementDetailsDto.EmployeeInfo mapEmployee(
            EmployeeFinalSettlement settlement) {

        return new FinalSettlementDetailsDto.EmployeeInfo(

                settlement.getEmployeeId(),

                settlement.getEmployeeCode(),

                settlement.getEmployeeName(),

                settlement.getDepartment(),

                settlement.getSection(),

                settlement.getDesignation(),

                settlement.getGrade(),

                settlement.getGradeType(),

                settlement.getHireDate(),

                settlement.getGratuityStartDate()
        );
    }


    private FinalSettlementDetailsDto.EarnedSalary mapEarnedSalary(
            EmployeeFinalSettlement settlement) {

        return new FinalSettlementDetailsDto.EarnedSalary(

                settlement.getMonthlyBasicSalary(),

                settlement.getEarnedBasicSalary(),

                settlement.getAllowances(),

                settlement.getGrossSalary()
        );
    }


    private FinalSettlementDetailsDto.Gratuity mapGratuity(
            EmployeeFinalSettlement settlement) {

        return new FinalSettlementDetailsDto.Gratuity(

                settlement.getGratuityEligible(),

                settlement.getGratuityCompletedYears(),

                settlement.getGratuityAmount()
        );
    }

    private FinalSettlementDetailsDto.Advances mapAdvances(
            EmployeeFinalSettlement settlement) {

        return new FinalSettlementDetailsDto.Advances(

                settlement.getPermanentAdvance(),

                settlement.getShortTermAdvance(),

                settlement.getRfdAdvance(),

                settlement.getTotalAdvance()
        );
    }

    private FinalSettlementDetailsDto.PermanentAdvances mapPermanentAdvances(
            List<EmployeeFinalSettlementAdvanceDetail> advances) {

        BigDecimal total = BigDecimal.ZERO;

        List<FinalSettlementDetailsDto.PermanentAdvance> items =
                new ArrayList<>();

        for (EmployeeFinalSettlementAdvanceDetail a : advances) {

            total = total.add(a.getOutstandingAmount());

            items.add(

                    new FinalSettlementDetailsDto.PermanentAdvance(

                            a.getAdvanceId(),

                            a.getRequestCode(),

                            a.getReceivingDate(),

                            a.getAdvanceStatus(),

                            a.getDescription(),

                            a.getOriginalAmount(),

                            a.getRecoveredAmount(),

                            a.getOutstandingAmount(),

                            a.getMonthlyDeduction(),

                            a.getBonusOffset(),

                            a.getAnnualLeaveOffset(),

                            a.getBonusRecovered(),

                            a.getLeaveRecovered(),

                            a.getSettlementRecovered()
                    )
            );
        }

        return new FinalSettlementDetailsDto.PermanentAdvances(

                items,

                total
        );
    }

    private FinalSettlementDetailsDto.Bonus mapBonus(

            EmployeeFinalSettlement settlement,

            List<EmployeeFinalSettlementBonusDetail> bonuses) {

        return new FinalSettlementDetailsDto.Bonus(

                settlement.getOutstandingBonus(),

                bonuses.stream()
                        .map(b -> new FinalSettlementDetailsDto.OutstandingBonus(

                                b.getBonusEventId(),

                                b.getEventName(),

                                b.getEventType(),

                                b.getReferenceDate(),

                                b.getEligibleAmount(),

                                b.getPaidAmount(),

                                b.getOutstandingAmount()

                        ))
                        .toList()
        );
    }


    private FinalSettlementDetailsDto.LeaveSummary mapAnnualLeave(EmployeeFinalSettlementLeaveDetail leave) {

        if (leave == null) {
            return null;
        }

        return new FinalSettlementDetailsDto.LeaveSummary(

                leave.getLeaveTypeId(),

                leave.getLeaveCode(),

                leave.getLeaveName(),

                leave.getCycleStart(),

                leave.getCycleEnd(),

                leave.getAllocated(),

                leave.getEarned(),

                leave.getUsed(),

                leave.getPending(),

                leave.getPayableDays(),

                leave.getExcessiveDays(),

                leave.getEncashmentAmount(),

                leave.getExcessiveAmount()
        );
    }

    private FinalSettlementDetailsDto.CombinedLeaveSummary mapMedicalCasualLeave(EmployeeFinalSettlementLeaveDetail leave) {

        if (leave == null) {
            return null;
        }

        return new FinalSettlementDetailsDto.CombinedLeaveSummary(

                leave.getAllocated(),

                leave.getEarned(),

                leave.getUsed(),

                leave.getPending(),

                leave.getPayableDays(),

                leave.getExcessiveDays(),

                leave.getEncashmentAmount(),

                leave.getExcessiveAmount()
        );
    }


    private List<FinalSettlementDetailsDto.OutstandingDeduction> mapDeductions(
            List<EmployeeFinalSettlementDeduction> deductions) {

        return deductions.stream()

                .map(d -> new FinalSettlementDetailsDto.OutstandingDeduction(

                        d.getDeductionCode(),

                        d.getDeductionName(),

                        d.getAmount()

                ))

                .toList();
    }


    private FinalSettlementDetailsDto.AttendanceDetail mapAttendanceDetail(
            List<EmployeeFinalSettlementAttendance> attendanceSnapshots) {

        if (attendanceSnapshots == null || attendanceSnapshots.isEmpty()) {
            return null;
        }

        EmployeeFinalSettlementAttendance snapshot = attendanceSnapshots.getFirst();

        return new FinalSettlementDetailsDto.AttendanceDetail(

                snapshot.getPeriodFrom(),

                snapshot.getPeriodTo(),

                snapshot.getPresentDays(),

                snapshot.getHalfDays(),

                snapshot.getLeaveDays(),

                snapshot.getPaidLeaveDays(),

                snapshot.getUnpaidLeaveDays(),

                snapshot.getAbsentDays(),

                snapshot.getWeekendDays(),

                snapshot.getHolidayDays()
        );
    }

    private FinalSettlementDetailsDto.NormalOvertimeDetail mapOvertimeDetail(
            List<EmployeeFinalSettlementOvertime> overtimeSnapshots) {

        if (overtimeSnapshots == null || overtimeSnapshots.isEmpty()) {
            return null;
        }

        EmployeeFinalSettlementOvertime snapshot = overtimeSnapshots.getFirst();

        return new FinalSettlementDetailsDto.NormalOvertimeDetail(

                snapshot.getApprovedMinutes(),

                snapshot.getApprovedAmount(),

                snapshot.getPendingMinutes(),

                snapshot.getPendingAmount(),

                snapshot.getUnappliedMinutes(),

                snapshot.getUnappliedAmount(),

                snapshot.getTotalMinutes(),

                snapshot.getTotalAmount()
        );
    }


    private FinalSettlementDetailsDto.FairPriceShop mapFairPriceShop(
            EmployeeFinalSettlement settlement) {

        return new FinalSettlementDetailsDto.FairPriceShop(

                settlement.getFairPriceShopAmount()
        );
    }

    private FinalSettlementDetailsDto.Canteen mapCanteen(
            EmployeeFinalSettlement settlement) {

        return new FinalSettlementDetailsDto.Canteen(

                settlement.getCanteenAmount()
        );
    }


    private FinalSettlementDetailsDto.Totals mapTotals(
            EmployeeFinalSettlement settlement) {

        return new FinalSettlementDetailsDto.Totals(

                settlement.getGrossSettlement(),

                settlement.getTotalDeductions(),

                settlement.getTaxAmount(),

                settlement.getNetSettlement()
        );
    }


    private FinalSettlementDetailsDto.Audit mapAudit(
            EmployeeFinalSettlement settlement) {

        return new FinalSettlementDetailsDto.Audit(

                settlement.getCreatedBy(),
                settlement.getCreatedAt(),

                settlement.getApprovedBy(),
                settlement.getApprovedAt(),

                settlement.getPaidBy(),
                settlement.getPaidAt(),

                settlement.getUpdatedAt()
        );
    }

}
