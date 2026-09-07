package com.hozgan.smartpay.notification.service;

import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.notification.model.RecipientProfile;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DefaultRecipientResolver implements RecipientResolver {

    private final ConcurrentMap<String, RecipientProfile> carrierProfiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RecipientProfile> shipperProfiles = new ConcurrentHashMap<>();

    public void registerCarrier(CarrierId carrierId, RecipientProfile profile) {
        carrierProfiles.put(carrierId.asString(), profile);
    }

    public void registerShipper(ShipperId shipperId, RecipientProfile profile) {
        shipperProfiles.put(shipperId.asString(), profile);
    }

    @Override
    public RecipientProfile resolveCarrier(CarrierId carrierId) {
        return carrierProfiles.computeIfAbsent(carrierId.asString(),
                id -> RecipientProfile.defaultCarrier("Carrier-" + id.substring(0, Math.min(id.length(), 8))));
    }

    @Override
    public RecipientProfile resolveShipper(ShipperId shipperId) {
        return shipperProfiles.computeIfAbsent(shipperId.asString(),
                id -> RecipientProfile.defaultShipper("Shipper-" + id.substring(0, Math.min(id.length(), 8))));
    }
}
