package com.hozgan.smartpay.payment.mapper;

import com.hozgan.smartpay.common.event.PaymentInitiatedEvent;
import com.hozgan.smartpay.payment.dto.response.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(expression = "java(event.paymentId().toString())", target = "paymentId")
    @Mapping(source = "occurredAt", target = "createdAt")
    @Mapping(expression = "java(event.amount().toMinorUnits())", target = "amountInPence")
    @Mapping(expression = "java(event.amount().currency().getCurrencyCode())", target = "currency")
    @Mapping(expression = "java(event.amount().amount().toPlainString())", target = "amount")
    @Mapping(expression = "java(event.endToEndId().value())", target = "endToEndId")
    @Mapping(expression = "java(event.debtorAccountId().toString())", target = "debtorAccountId")
    @Mapping(expression = "java(event.creditorAccountId().toString())", target = "creditorAccountId")
    @Mapping(constant = "INITIATED", target = "status")
    PaymentResponse toResponse(PaymentInitiatedEvent event);
}
