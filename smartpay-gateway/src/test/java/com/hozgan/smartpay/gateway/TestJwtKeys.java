package com.hozgan.smartpay.gateway;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only RS256 JWT minting: generates a fresh RSA key pair and signs compact JWTs the same
 * way a real identity provider would, so edge verification can be exercised end-to-end.
 */
public final class TestJwtKeys {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestJwtKeys() {
    }

    public static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    public static String publicKeyPem(KeyPair keyPair) {
        String base64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    public static String token(KeyPair keyPair, String subject, String tenantId, Instant expiresAt) {
        return token(keyPair, subject, tenantId, List.of(), expiresAt, null);
    }

    public static String token(KeyPair keyPair, String subject, String tenantId, List<String> roles,
                               Instant expiresAt, String issuer) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", subject);
        claims.put("tenant_id", tenantId);
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        if (issuer != null) {
            claims.put("iss", issuer);
        }
        if (!roles.isEmpty()) {
            claims.put("roles", roles);
        }
        return tokenWithClaims(keyPair, claims);
    }

    /**
     * Signs a compact JWT over caller-supplied claims — used to exercise claim-level rejection
     * with a cryptographically valid signature.
     */
    public static String tokenWithClaims(KeyPair keyPair, Map<String, Object> claims) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        try {
            String headerSegment = base64Url(MAPPER.writeValueAsBytes(header));
            String payloadSegment = base64Url(MAPPER.writeValueAsBytes(claims));
            String signingInput = headerSegment + "." + payloadSegment;
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(signer.sign());
        } catch (JacksonException e) {
            throw new IllegalStateException("Token claim serialization failed", e);
        } catch (Exception e) {
            throw new IllegalStateException("Token signing failed", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
