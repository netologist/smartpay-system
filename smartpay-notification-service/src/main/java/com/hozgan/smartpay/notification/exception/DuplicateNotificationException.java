package com.hozgan.smartpay.notification.exception;

import java.util.UUID;

public class DuplicateNotificationException extends NotificationException {

    private final UUID eventId;
    private final String channel;

    public DuplicateNotificationException(UUID eventId, String channel) {
        super("Notification already dispatched for eventId: " + eventId + ", channel: " + channel,
                "ERR_DUPLICATE_NOTIFICATION");
        this.eventId = eventId;
        this.channel = channel;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getChannel() {
        return channel;
    }
}
