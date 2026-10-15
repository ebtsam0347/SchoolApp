package com.forward.hrms.employee.controller;

import com.forward.hrms.employee.dto.request.BelongingMasterRequest;
import com.forward.hrms.employee.dto.request.BelongingMasterUpdateRequest;
import com.forward.hrms.employee.dto.response.BelongingMasterResponse;
import com.forward.hrms.employee.service.BelongingMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Belonging Master", description = "Employee belonging master controller.")
@RestController
@RequestMapping("/belonging-master")
@RequiredArgsConstructor
public class BelongingMasterController {

    private final BelongingMasterService service;

    @Operation(summary = "Create Belonging Master",
            description = "Creates a new belonging master after validating the authorized employee and ensuring the belonging name is unique."
    )
    @PostMapping
    public BelongingMasterResponse create(
            @Valid @RequestBody BelongingMasterRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Update Belonging Master",
            description = "Updates an existing belonging master after validating the authorized employee and checking for duplicate belonging names."
    )
    @PutMapping("/{id}")
    public BelongingMasterResponse update(
            @PathVariable Long id,
            @Valid @RequestBody BelongingMasterUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Get Belonging Master by ID",
            description = "Retrieves a belonging master using its unique identifier."
    )
    @GetMapping("/{id}")
    public BelongingMasterResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @Operation(summary = "Get All Belonging Masters",
            description = "Returns a list of all belonging master records."
    )
    @GetMapping
    public List<BelongingMasterResponse> getAll() {
        return service.getAll();
    }

    @Operation(summary = "Get Active Belonging Masters",
            description = "Returns only the active belonging master records."
    )
    @GetMapping("/active")
    public List<BelongingMasterResponse> getActive() {
        return service.getActive();
    }

    @Operation(summary = "Change Belonging Master Status",
            description = "Activates or deactivates a belonging master."
    )
    @PatchMapping("/{id}/status")
    public BelongingMasterResponse changeStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return service.changeStatus(id, active);
    }
    @Operation(summary = "Get belonging that i am authorized", description = "Fetch all those belonging that HR authorized me for them.")
    @GetMapping("/belongings-authorized-to-me")
    public List<BelongingMasterResponse> getBelongingsAuthorizedToMe(){
        return service.getBelongingsAuthorizedToMe();
    }
}
