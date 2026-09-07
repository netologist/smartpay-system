package com.hozgan.smartpay.notification.exception;

public class TemplateNotFoundException extends NotificationException {

    public TemplateNotFoundException(String templateCode, String channel) {
        super("Notification template not found or inactive for code: " + templateCode + ", channel: " + channel,
                "ERR_TEMPLATE_NOT_FOUND");
    }
}
