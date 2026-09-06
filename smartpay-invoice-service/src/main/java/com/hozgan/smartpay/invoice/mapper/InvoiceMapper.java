package com.hozgan.smartpay.invoice.mapper;

import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.invoice.dto.response.EpodRecordResponse;
import com.hozgan.smartpay.invoice.dto.response.InvoiceResponse;
import com.hozgan.smartpay.invoice.dto.response.PricingBreakdownResponse;
import com.hozgan.smartpay.invoice.entity.EpodRecordEntity;
import com.hozgan.smartpay.invoice.entity.InvoiceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {

    @Mapping(source = "id", target = "epodId")
    EpodRecordResponse toEpodResponse(EpodRecordEntity entity);

    @Mapping(source = "invoiceId", target = "invoiceId")
    @Mapping(source = "pricing", target = "pricing")
    InvoiceResponse toInvoiceResponse(InvoiceEntity entity);

    PricingBreakdownResponse toPricingBreakdownResponse(InvoicePricing pricing);
}
