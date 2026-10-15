package com.forward.hrms.notification.controller;

import com.forward.hrms.notification.dto.request.PushSubscribeRequest;
import com.forward.hrms.notification.dto.request.PushUnsubscribeRequest;
import com.forward.hrms.notification.dto.response.PushConfigResponse;
import com.forward.hrms.notification.service.PushService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications — Web Push",
        description = "Browser push subscriptions: OS-level notifications on desktop and mobile, even with the app closed.")
@RestController
@RequestMapping("/notifications/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushService service;

    @Operation(summary = "Web Push config",
            description = "Whether push is enabled on this server and the VAPID public key to subscribe with.")
    @GetMapping("/config")
    public PushConfigResponse config() {
        return service.config();
    }

    @Operation(summary = "Register this browser",
            description = "Upsert by endpoint — re-binds the subscription to the caller if another user had it. "
                    + "422 PUSH_NOT_CONFIGURED when the server has no VAPID keys; 400 for an unknown push service or bad keys.")
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody PushSubscribeRequest req) {
        service.subscribe(req);
    }

    @Operation(summary = "Unregister this browser",
            description = "Idempotent. Called when the user turns push off or signs out.")
    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody PushUnsubscribeRequest req) {
        service.unsubscribe(req.endpoint());
    }
}
