package com.hozgan.smartpay.notification.service;

import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.notification.model.RecipientProfile;

public interface RecipientResolver {

    RecipientProfile resolveCarrier(CarrierId carrierId);

    RecipientProfile resolveShipper(ShipperId shipperId);
}
