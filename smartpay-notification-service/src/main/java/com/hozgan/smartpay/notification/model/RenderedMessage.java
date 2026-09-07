package com.hozgan.smartpay.notification.model;

import java.util.Objects;

public record RenderedMessage(
        String subject,
        String content
) {
    public RenderedMessage {
        Objects.requireNonNull(content, "content cannot be null");
    }

    public static RenderedMessage of(String content) {
        return new RenderedMessage(null, content);
    }

    public static RenderedMessage of(String subject, String content) {
        return new RenderedMessage(subject, content);
    }
}
