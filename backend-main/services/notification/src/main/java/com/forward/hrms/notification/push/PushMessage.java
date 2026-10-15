package com.forward.hrms.notification.push;

/**
 * A notification snapshotted for delivery: recipient, the JSON the service
 * worker renders, and whether to ask the push service for high urgency.
 */
public record PushMessage(long userId, byte[] payload, boolean urgent) {
}
