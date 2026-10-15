package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.dto.response.FinalSettlementListDto;
import com.forward.hrms.payroll.dto.response.FinalSettlementSearchRequest;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.service.settlements.specification.FinalSettlementSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinalSettlementListService {

    private final EmployeeFinalSettlementRepository repository;

    public Page<FinalSettlementListDto> search(
            FinalSettlementSearchRequest request,
            Pageable pageable) {

        return repository.findAll(
                        FinalSettlementSpecification.search(request),
                        pageable
                )
                .map(this::map);
    }

    private FinalSettlementListDto map(EmployeeFinalSettlement settlement) {

        return FinalSettlementListDto.builder()

                .settlementId(settlement.getId())

                .employeeId(settlement.getEmployeeId())

                .employeeCode(settlement.getEmployeeCode())

                .employeeName(settlement.getEmployeeName())

                .department(settlement.getDepartment())

                .section(settlement.getSection())

                .designation(settlement.getDesignation())

                .grade(settlement.getGrade())

                .gradeType(settlement.getGradeType())

                .hireDate(settlement.getHireDate())

                .lastWorkingDay(settlement.getLastWorkingDay())

                .status(settlement.getStatus())

                .grossSettlement(settlement.getGrossSettlement())

                .totalDeductions(settlement.getTotalDeductions())

                .netSettlement(settlement.getNetSettlement())

                .remarks(settlement.getRemarks())

                .createdAt(settlement.getCreatedAt())

                .build();
    }

}
