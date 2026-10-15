package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.service.NotificationStreamService;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.CommonErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notifications — Live stream",
        description = "Server-Sent Events channel for the bell badge + inbox. The browser opens one "
                + "connection per tab; the BFF subscribes to the caller's Redis pub/sub channel and "
                + "forwards each notification message as a 'notification' event. The SPA invalidates "
                + "the relevant React-Query keys on receipt so the badge and feed rerender immediately.")
@RestController
@RequestMapping("/bff/notifications")
@RequiredArgsConstructor
public class NotificationStreamController {

    private final NotificationStreamService service;

    @Operation(summary = "Open the live-notification SSE channel",
            description = "Authenticated; returns a long-lived `text/event-stream` response. The first event "
                    + "is `ready` (connection established), then one `notification` event per published "
                    + "message, plus periodic comment-line keepalives. Browsers reconnect automatically on "
                    + "EOF; the server-side timeout (10 min default) is the safety net for stuck connections.")
    @ApiResponse(responseCode = "200", description = "SSE stream opened.",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE))
    @ApiResponse(responseCode = "401", description = "Missing identity headers (gateway didn't authenticate).")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream() {
        long userId = CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no current user"));
        return service.open(userId);
    }
}
