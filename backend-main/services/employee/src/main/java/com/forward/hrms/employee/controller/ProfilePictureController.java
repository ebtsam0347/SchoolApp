package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.ProfilePictureFilenameRequest;
import com.forward.hrms.employee.dto.request.SetProfilePictureRequest;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.UploadUrlResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.service.EmployeeDocumentService;
import com.forward.hrms.employee.dto.response.EmployeeDtoMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Employees — Profile Picture",
        description = "Avatar upload uses the same two-step flow as documents but lives on a separate column " +
                "so list-view queries don't need to join the documents table. Requires `employee.write`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/employees/{id}/profile-picture")
public class ProfilePictureController {

    private final EmployeeDocumentService docs;

    @Operation(summary = "Mint a pre-signed PUT URL for a new profile picture", description = "Requires `employee.write`.")
    @PostMapping("/upload-url")
    @RequirePermission("employee.write")
    public UploadUrlResponse uploadUrl(@PathVariable long id,
                                       @Valid @RequestBody ProfilePictureFilenameRequest req) {
        return docs.signProfilePictureUploadUrl(id, req.filename(), req.bytes());
    }

    @Operation(summary = "Set the profile picture once the upload completes",
            description = "Verifies the object exists, deletes the previous picture (best-effort), " +
                    "and updates `employees.profile_picture_key`. Requires `employee.write`.")
    @PostMapping
    @RequirePermission("employee.write")
    public EmployeeDto setProfilePicture(@PathVariable long id,
                                         @Valid @RequestBody SetProfilePictureRequest req) {
        Employee e = docs.setProfilePicture(id, req.objectKey());
        return EmployeeDtoMapper.of(e);
    }
}
