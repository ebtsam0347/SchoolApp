package com.forward.hrms.auth.dto.request;

import com.forward.hrms.auth.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateUserRequest", description = "Patch a user. All fields optional — null means \"leave unchanged\".")
public record UpdateUserRequest(
        @Schema(description = "New display name.", example = "Ahmed M. Raza", maxLength = 128)
        @Size(max = 128) String fullName,

        @Schema(description = "New contact email.", example = "ahmed@forwardsports.com", maxLength = 255)
        @Email @Size(max = 255) String email,

        @Schema(description = "Switch account status. Use DISABLED to lock the user out without deleting them.")
        UserStatus status
) {}
