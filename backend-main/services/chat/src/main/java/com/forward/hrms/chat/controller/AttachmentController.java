package com.forward.hrms.chat.controller;

import com.forward.hrms.chat.dto.request.UploadUrlRequest;
import com.forward.hrms.chat.dto.response.UploadUrlResponse;
import com.forward.hrms.chat.service.AttachmentService;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat — Attachments",
        description = "Two-step upload: ask for a signed PUT URL, upload to MinIO, then include the objectKey in the next send-message request.")
@RestController
@RequestMapping("/chat/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService service;

    @Operation(summary = "Mint a pre-signed PUT URL for a chat attachment",
            description = "Validates content-type whitelist + size cap. The returned objectKey is bound to the caller's user prefix; only the caller can later attach this object to a message.")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "415", description = "Content-type not allowed.")
    @ApiResponse(responseCode = "409", description = "Size exceeds the per-attachment cap.")
    @PostMapping("/upload-url")
    public UploadUrlResponse uploadUrl(@Valid @RequestBody UploadUrlRequest req) {
        return service.signUploadUrl(callerUserId(), req);
    }

    private static long callerUserId() {
        return CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no current user"));
    }
}
