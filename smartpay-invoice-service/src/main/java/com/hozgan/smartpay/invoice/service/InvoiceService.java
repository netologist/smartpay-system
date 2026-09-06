package com.hozgan.smartpay.invoice.service;

import com.hozgan.smartpay.common.event.InvoiceIssuedEvent;
import com.hozgan.smartpay.common.exception.DuplicateLoadException;
import com.hozgan.smartpay.common.exception.EntityNotFoundException;
import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.invoice.entity.InvoiceEntity;
import com.hozgan.smartpay.invoice.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final FreightPricingEngine pricingEngine;
    private final ApplicationEventPublisher eventPublisher;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          FreightPricingEngine pricingEngine,
                          ApplicationEventPublisher eventPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.pricingEngine = pricingEngine;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public InvoiceEntity createInvoice(LoadId loadId,
                                       ShipperId shipperId,
                                       CarrierId carrierId,
                                       VehicleType vehicleType,
                                       BigDecimal mileageMiles,
                                       String currencyCode) {
        Objects.requireNonNull(loadId, "loadId cannot be null");
        Objects.requireNonNull(shipperId, "shipperId cannot be null");
        Objects.requireNonNull(carrierId, "carrierId cannot be null");
        Objects.requireNonNull(vehicleType, "vehicleType cannot be null");
        Objects.requireNonNull(mileageMiles, "mileageMiles cannot be null");

        // 1. Prevent duplicate invoices for the same load (AC-4)
        if (invoiceRepository.findByLoadId(loadId).isPresent()) {
            log.warn("Invoice already exists for load: {}", loadId);
            throw new DuplicateLoadException(loadId);
        }

        // 2. Dynamic pricing calculation (AC-2, AC-3)
        InvoicePricing pricing = pricingEngine.calculate(vehicleType, mileageMiles, currencyCode);

        // 3. Persist freight invoice
        InvoiceEntity invoice = new InvoiceEntity(
                loadId,
                shipperId,
                carrierId,
                vehicleType,
                mileageMiles,
                pricing,
                InvoiceStatus.EPOD_VERIFIED
        );

        InvoiceEntity saved = invoiceRepository.save(invoice);
        log.info("Invoice issued for load: {}, invoiceId: {}, total: {}",
                loadId, saved.getId(), saved.getTotalAmountPence());

        // 4. Emit InvoiceIssuedEvent
        InvoiceIssuedEvent event = InvoiceIssuedEvent.of(
                saved.getInvoiceId(),
                loadId,
                shipperId,
                carrierId,
                pricing
        );
        eventPublisher.publishEvent(event);

        return saved;
    }

    @Transactional(readOnly = true)
    public InvoiceEntity getInvoiceById(InvoiceId invoiceId) {
        return invoiceRepository.findById(invoiceId.value())
                .orElseThrow(() -> new EntityNotFoundException("Invoice", invoiceId));
    }

    @Transactional(readOnly = true)
    public InvoiceEntity getInvoiceById(UUID id) {
        return getInvoiceById(InvoiceId.of(id));
    }

    @Transactional(readOnly = true)
    public InvoiceEntity getInvoiceByLoadId(LoadId loadId) {
        return invoiceRepository.findByLoadId(loadId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice", loadId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceEntity> findByCarrierAndStatus(CarrierId carrierId, InvoiceStatus status) {
        return invoiceRepository.findByCarrierIdAndStatus(carrierId, status);
    }

    @Transactional(readOnly = true)
    public List<InvoiceEntity> findByShipperId(ShipperId shipperId) {
        return invoiceRepository.findByShipperId(shipperId);
    }

    /**
     * AC-5: Settlement Mutability Lock
     * Prevents cancellation if invoice is already SETTLED.
     */
    @Transactional
    public InvoiceEntity cancelInvoice(InvoiceId invoiceId) {
        InvoiceEntity invoice = getInvoiceById(invoiceId);
        invoice.cancel();
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public InvoiceEntity cancelInvoice(UUID id) {
        return cancelInvoice(InvoiceId.of(id));
    }

    /**
     * AC-5: Settlement Mutability Lock
     * Prevents status update if invoice is already SETTLED.
     */
    @Transactional
    public InvoiceEntity updateInvoiceStatus(InvoiceId invoiceId, InvoiceStatus newStatus) {
        InvoiceEntity invoice = getInvoiceById(invoiceId);
        invoice.updateStatus(newStatus);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public InvoiceEntity updateInvoiceStatus(UUID id, InvoiceStatus newStatus) {
        return updateInvoiceStatus(InvoiceId.of(id), newStatus);
    }
}
