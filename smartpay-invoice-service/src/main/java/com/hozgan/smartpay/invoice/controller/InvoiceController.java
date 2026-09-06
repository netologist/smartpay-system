package com.hozgan.smartpay.invoice.controller;

import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.invoice.dto.request.CreateInvoiceRequest;
import com.hozgan.smartpay.invoice.dto.response.InvoiceResponse;
import com.hozgan.smartpay.invoice.entity.InvoiceEntity;
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

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
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
                .body(InvoiceResponse.from(invoice));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable UUID id) {
        InvoiceEntity invoice = invoiceService.getInvoiceById(id);
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    @GetMapping("/load/{loadId}")
    public ResponseEntity<InvoiceResponse> getInvoiceByLoadId(@PathVariable String loadId) {
        InvoiceEntity invoice = invoiceService.getInvoiceByLoadId(loadId);
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<InvoiceResponse> cancelInvoice(@PathVariable UUID id) {
        log.info("Requesting cancellation for invoice: {}", id);
        InvoiceEntity invoice = invoiceService.cancelInvoice(id);
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<InvoiceResponse> updateStatus(@PathVariable UUID id,
                                                        @RequestParam("status") InvoiceStatus status) {
        log.info("Updating status for invoice {} to {}", id, status);
        InvoiceEntity invoice = invoiceService.updateInvoiceStatus(id, status);
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }
}
