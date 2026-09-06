package com.hozgan.smartpay.payout.web;

import com.hozgan.smartpay.common.event.EpodVerifiedEvent;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome;
import com.hozgan.smartpay.payout.service.FactoringPayoutWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Diagnostic and operations management controller for Factoring Payout Worker.
 * Resides under .web package adhering to AGENTS.md architectural standards.
 */
@RestController
@RequestMapping("/api/v1/payouts")
public class FactoringWorkerController {

    private static final Logger log = LoggerFactory.getLogger(FactoringWorkerController.class);

    private final FactoringPayoutWorker payoutWorker;

    public FactoringWorkerController(FactoringPayoutWorker payoutWorker) {
        this.payoutWorker = payoutWorker;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getWorkerStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "ACTIVE",
                "worker", "smartpay-payout-worker",
                "virtualThreads", true
        ));
    }

    @PostMapping("/process-epod")
    public ResponseEntity<FactoringPayoutOutcome> processEpodManually(@RequestBody EpodVerifiedEvent event) {
        log.info("Manual ePOD verification trigger for loadId={}", event.loadId());
        FactoringPayoutOutcome outcome = payoutWorker.processDeliveryVerification(event);
        return ResponseEntity.ok(outcome);
    }
}
