package com.hozgan.smartpay.gateway.service;

import java.util.List;

/**
 * Claims extracted from a verified edge JWT. {@code roles} is the raw claim list; downstream
 * authorization is enforced by each microservice.
 */
public record JwtPrincipal(String subject, String tenantId, List<String> roles) {
}
