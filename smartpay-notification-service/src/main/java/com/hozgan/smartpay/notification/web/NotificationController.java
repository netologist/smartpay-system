package com.hozgan.smartpay.notification.web;

import com.hozgan.smartpay.notification.dto.request.ManualNotificationRequest;
import com.hozgan.smartpay.notification.dto.response.NotificationLogResponse;
import com.hozgan.smartpay.notification.entity.NotificationLogEntity;
import com.hozgan.smartpay.notification.mapper.NotificationMapper;
import com.hozgan.smartpay.notification.model.NotificationDispatchResult;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import com.hozgan.smartpay.notification.repository.NotificationLogRepository;
import com.hozgan.smartpay.notification.service.NotificationDispatchService;
import com.hozgan.smartpay.notification.service.TemplateEngine;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationLogRepository logRepository;
    private final NotificationDispatchService dispatchService;
    private final TemplateEngine templateEngine;
    private final NotificationMapper mapper;

    public NotificationController(
            NotificationLogRepository logRepository,
            NotificationDispatchService dispatchService,
            TemplateEngine templateEngine,
            NotificationMapper mapper) {
        this.logRepository = logRepository;
        this.dispatchService = dispatchService;
        this.templateEngine = templateEngine;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationLogResponse> getNotificationById(@PathVariable UUID id) {
        return logRepository.findById(id)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<NotificationLogResponse>> getNotifications(
            @RequestParam(required = false) UUID eventId) {
        List<NotificationLogEntity> logs;
        if (eventId != null) {
            logs = logRepository.findByEventId(eventId);
        } else {
            logs = logRepository.findAll();
        }
        return ResponseEntity.ok(mapper.toResponseList(logs));
    }

    @PostMapping("/dispatch")
    public ResponseEntity<NotificationLogResponse> manualDispatch(
            @Valid @RequestBody ManualNotificationRequest request) {

        Map<String, String> params = request.parameters() != null ? request.parameters() : Map.of();
        RenderedMessage renderedMessage = templateEngine.render(
                request.templateCode(),
                request.channel(),
                params
        );

        NotificationDispatchResult result = dispatchService.dispatch(
                request.eventId(),
                request.eventType(),
                request.channel(),
                request.recipient(),
                request.templateCode(),
                renderedMessage,
                params
        );

        return logRepository.findById(result.notificationId())
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.internalServerError().build());
    }
}
