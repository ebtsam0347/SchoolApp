package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.RecordDocumentRequest;
import com.forward.hrms.employee.dto.request.UploadUrlRequest;
import com.forward.hrms.employee.dto.response.EmployeeDocumentDto;
import com.forward.hrms.employee.dto.response.UploadUrlResponse;
import com.forward.hrms.employee.service.EmployeeDocumentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Employees — Documents",
        description = "Two-step document upload: ask for a signed PUT URL, upload to MinIO, then POST metadata. Requires `employee.read` / `employee.write`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/employees/{id}/documents")
public class EmployeeDocumentController {

    private final EmployeeDocumentService service;

    @Operation(summary = "List an employee's documents",
            description = "Paginated. Each row carries a short-lived signed download URL the client can use directly. " +
                    "Default page size is 50; ordered newest-first. Requires `employee.read`.")
    @GetMapping
    @RequirePermission("employee.read")
    public PageResponse<EmployeeDocumentDto> list(@PathVariable long id,
                                                  @Parameter(hidden = true) Pageable pageable) {
        Pageable p = PageableDefaults.apply(pageable, 50, 200,
                org.springframework.data.domain.Sort.by("id").descending());
        return service.list(id, p);
    }

    @Operation(summary = "Mint a pre-signed PUT URL the client uploads to directly",
            description = "Returned `objectKey` must be passed back when recording metadata. " +
                    "URL TTL is configurable via `hrms.employee.documents.upload-url-ttl`. Requires `employee.write`.")
    @PostMapping("/upload-url")
    @RequirePermission("employee.write")
    public UploadUrlResponse uploadUrl(@PathVariable long id, @Valid @RequestBody UploadUrlRequest req) {
        return service.signUploadUrl(id, req);
    }

    @Operation(summary = "Record metadata after the upload completes",
            description = "Service stats the object in MinIO; if the upload didn't actually happen the call returns 422. Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Recorded.")
    @ApiResponse(responseCode = "422", description = "Object not in storage.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeDocumentDto record(@PathVariable long id, @Valid @RequestBody RecordDocumentRequest req) {
        return service.record(id, req);
    }

    @Operation(summary = "Delete a document (storage object + metadata row)", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @DeleteMapping("/{docId}")
    @RequirePermission("employee.write")
    public ResponseEntity<Void> delete(@PathVariable long id, @PathVariable long docId) {
        service.delete(id, docId);
        return ResponseEntity.noContent().build();
    }
}
