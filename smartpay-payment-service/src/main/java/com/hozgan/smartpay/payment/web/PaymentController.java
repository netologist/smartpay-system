package com.hozgan.smartpay.payment.web;

import tools.jackson.databind.ObjectMapper;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.PaymentId;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.payment.dto.request.PaymentRequest;
import com.hozgan.smartpay.payment.dto.response.PaymentResponse;
import com.hozgan.smartpay.payment.dto.response.PaymentStatusResponse;
import com.hozgan.smartpay.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKeyHeader,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantIdHeader,
            @Valid @RequestBody PaymentRequest request) throws Exception {

        TenantId tenantId = TenantId.of(request.tenantId());
        IdempotencyKey idempotencyKey = IdempotencyKey.of(idempotencyKeyHeader);
        byte[] rawBody = objectMapper.writeValueAsBytes(request);

        PaymentResponse response = paymentService.initiatePayment(tenantId, idempotencyKey, rawBody, request);

        log.info("Payment initiated: paymentId={} idempotencyKey={}", response.paymentId(), idempotencyKeyHeader);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("X-Payment-Id", response.paymentId())
                .body(response);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable("id") String id) {
        PaymentId paymentId = PaymentId.of(id);
        PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId);
        return ResponseEntity.ok(response);
    }
}
