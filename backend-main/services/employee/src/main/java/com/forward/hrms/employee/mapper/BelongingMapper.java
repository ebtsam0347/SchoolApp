package com.forward.hrms.employee.mapper;

import com.forward.hrms.employee.dto.request.BelongingMasterRequest;
import com.forward.hrms.employee.dto.response.BelongingClearanceCheckDto;
import com.forward.hrms.employee.dto.response.BelongingIssuanceResponse;
import com.forward.hrms.employee.dto.response.BelongingMasterResponse;
import com.forward.hrms.employee.entity.BelongingIssuance;
import com.forward.hrms.employee.entity.BelongingMaster;

public class BelongingMapper {
    private BelongingMapper() {
    }

    public static BelongingMaster toEntity(BelongingMasterRequest request) {

        return BelongingMaster.builder()
                .belongingName(request.belongingName())
                .authorizedEmployeeId(request.authorizedEmployeeId())
                .active(request.active() == null || request.active())
                .build();
    }

    public static BelongingMasterResponse toResponse(BelongingMaster entity) {

        return new BelongingMasterResponse(
                entity.getId(),
                entity.getBelongingName(),
                entity.getAuthorizedEmployeeId(),
                entity.getActive()
        );
    }

    /**
     * Blocking row for the termination clearance check. Drops the buy-back
     * percentages and the activation date the full issuance response
     * carries — HR only needs to know what is held, what it is worth and
     * which row to clear.
     */
    public static BelongingClearanceCheckDto.PendingBelonging toPendingBelonging(BelongingIssuance entity) {

        return new BelongingClearanceCheckDto.PendingBelonging(
                entity.getId(),
                entity.getBelongingMaster().getId(),
                entity.getBelongingMaster().getBelongingName(),
                entity.getAssetModel(),
                entity.getAssetYear(),
                entity.getAssetPrice(),
                entity.getIssuedDate(),
                entity.getExpiryDate(),
                entity.getCondition(),
                entity.getActivationStatus(),
                entity.getClearanceStatus(),
                entity.getClearanceId(),
                entity.getClearanceDate(),
                entity.getDescription()
        );
    }

    public static BelongingIssuanceResponse toResponse(BelongingIssuance entity) {

        return new BelongingIssuanceResponse(
                entity.getId(),
                entity.getBelongingMaster().getId(),
                entity.getBelongingMaster().getBelongingName(),
                entity.getEmployeeId(),
                entity.getAssetModel(),
                entity.getAssetYear(),
                entity.getAssetPrice(),
                entity.getIssuedDate(),
                entity.getExpiryDate(),
                entity.getBuyBackPercEmployee(),
                entity.getBuyBackPercEmployer(),
                entity.getCondition(),
                entity.getActivationStatus(),
                entity.getActivationDate(),
                entity.getDescription(),
                entity.getClearanceStatus(),
                entity.getClearanceId(),
                entity.getClearanceDate()
        );
    }
}
