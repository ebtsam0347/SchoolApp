package com.forward.hrms.employee.dto.request.travel.response;

public record TravelRequestExpenseResponse(
        String travelRequestId,
        String travelRequestDate,
        String travelRequestStartDate,
        String travelRequestEndDate
) {
}
