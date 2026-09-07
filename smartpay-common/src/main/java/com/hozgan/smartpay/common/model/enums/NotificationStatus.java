package com.hozgan.smartpay.common.model.enums;

/**
 * Lifecycle states of an event-driven notification dispatch.
 */
public enum NotificationStatus {
    PENDING,
    DISPATCHED,
    FAILED,
    DEAD_LETTERED
}
