package com.forward.hrms.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TypingRequest", description = "Fire-and-forget typing signal. The client sends `stopped: true` to clear early; otherwise the indicator self-clears client-side after a short timeout.")
public record TypingRequest(
        @Schema(description = "True to clear, false (or absent) to indicate typing.", nullable = true)
        Boolean stopped
) {
    public boolean isStopped() {
        return stopped != null && stopped;
    }
}
