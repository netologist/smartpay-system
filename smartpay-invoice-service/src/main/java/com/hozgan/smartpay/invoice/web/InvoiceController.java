package com.hozgan.smartpay.invoice.web;

import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.invoice.dto.request.CreateInvoiceRequest;
import com.hozgan.smartpay.invoice.dto.response.InvoiceResponse;
import com.hozgan.smartpay.invoice.entity.InvoiceEntity;
import com.hozgan.smartpay.invoice.mapper.InvoiceMapper;
import com.hozgan.smartpay.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;
    private final InvoiceMapper invoiceMapper;

    public InvoiceController(InvoiceService invoiceService, InvoiceMapper invoiceMapper) {
        this.invoiceService = invoiceService;
        this.invoiceMapper = invoiceMapper;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        log.info("Received invoice creation request for load: {}", request.loadId());

        InvoiceEntity invoice = invoiceService.createInvoice(
                request.loadId(),
                request.shipperId(),
                request.carrierId(),
                request.vehicleType(),
                request.mileageMiles(),
                request.currency()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(invoiceMapper.toInvoiceResponse(invoice));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable UUID id) {
        InvoiceId invoiceId = InvoiceId.of(id);
        InvoiceEntity invoice = invoiceService.getInvoiceById(invoiceId);
        return ResponseEntity.ok(invoiceMapper.toInvoiceResponse(invoice));
    }

    @GetMapping("/load/{loadId}")
    public ResponseEntity<InvoiceResponse> getInvoiceByLoadId(@PathVariable String loadId) {
        LoadId typedLoadId = LoadId.of(loadId);
        InvoiceEntity invoice = invoiceService.getInvoiceByLoadId(typedLoadId);
        return ResponseEntity.ok(invoiceMapper.toInvoiceResponse(invoice));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<InvoiceResponse> cancelInvoice(@PathVariable UUID id) {
        log.info("Requesting cancellation for invoice: {}", id);
        InvoiceId invoiceId = InvoiceId.of(id);
        InvoiceEntity invoice = invoiceService.cancelInvoice(invoiceId);
        return ResponseEntity.ok(invoiceMapper.toInvoiceResponse(invoice));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<InvoiceResponse> updateStatus(@PathVariable UUID id,
                                                        @RequestParam("status") InvoiceStatus status) {
        log.info("Updating status for invoice {} to {}", id, status);
        InvoiceId invoiceId = InvoiceId.of(id);
        InvoiceEntity invoice = invoiceService.updateInvoiceStatus(invoiceId, status);
        return ResponseEntity.ok(invoiceMapper.toInvoiceResponse(invoice));
    }
}
