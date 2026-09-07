package com.hozgan.smartpay.gateway.web.filter;

import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.gateway.service.JwtPrincipal;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Request attributes shared along the edge filter chain.
 */
public final class GatewayRequestAttributes {

    public static final String TENANT_ID = "smartpay.gateway.tenantId";
    public static final String USER_ID = "smartpay.gateway.userId";
    public static final String PRINCIPAL = "smartpay.gateway.principal";

    private GatewayRequestAttributes() {
    }

    public static void bind(HttpServletRequest request, JwtPrincipal principal) {
        request.setAttribute(TENANT_ID, TenantId.of(principal.tenantId()));
        request.setAttribute(USER_ID, principal.subject());
        request.setAttribute(PRINCIPAL, principal);
    }

    public static TenantId tenantId(HttpServletRequest request) {
        Object value = request.getAttribute(TENANT_ID);
        return value instanceof TenantId tenantId ? tenantId : null;
    }

    public static String userId(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ID);
        return value instanceof String userId ? userId : null;
    }
}
