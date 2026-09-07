package com.hozgan.smartpay.notification.provider;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.notification.model.ProviderReceipt;
import com.hozgan.smartpay.notification.model.RenderedMessage;

import java.util.Map;

public interface NotificationProvider {

    NotificationChannel channel();

    ProviderReceipt dispatch(String recipient, RenderedMessage message, Map<String, String> metadata);
}
