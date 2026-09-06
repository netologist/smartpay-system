package com.hozgan.smartpay.invoice.web;

import com.hozgan.smartpay.common.exception.EntityNotFoundException;
import com.hozgan.smartpay.invoice.dto.request.VerifyEpodRequest;
import com.hozgan.smartpay.invoice.dto.response.EpodRecordResponse;
import com.hozgan.smartpay.invoice.entity.EpodRecordEntity;
import com.hozgan.smartpay.invoice.mapper.InvoiceMapper;
import com.hozgan.smartpay.invoice.service.EpodService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/epod")
public class EpodController {

    private static final Logger log = LoggerFactory.getLogger(EpodController.class);

    private final EpodService epodService;
    private final InvoiceMapper invoiceMapper;

    public EpodController(EpodService epodService, InvoiceMapper invoiceMapper) {
        this.epodService = epodService;
        this.invoiceMapper = invoiceMapper;
    }

    @PostMapping("/verify")
    public ResponseEntity<EpodRecordResponse> verifyEpod(@Valid @RequestBody VerifyEpodRequest request) {
        log.info("Received ePOD verification request for load: {}", request.loadId());

        EpodRecordEntity saved = epodService.verifyAndRecordEpod(
                request.loadId(),
                request.carrierId(),
                request.deliveredAt(),
                request.latitude(),
                request.longitude(),
                request.photoS3Url(),
                request.signatureHash()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(invoiceMapper.toEpodResponse(saved));
    }

    @GetMapping("/{loadId}")
    public ResponseEntity<EpodRecordResponse> getEpodByLoadId(@PathVariable String loadId) {
        EpodRecordEntity epod = epodService.findByLoadId(loadId)
                .orElseThrow(() -> new EntityNotFoundException("EpodRecord", loadId));

        return ResponseEntity.ok(invoiceMapper.toEpodResponse(epod));
    }
}
