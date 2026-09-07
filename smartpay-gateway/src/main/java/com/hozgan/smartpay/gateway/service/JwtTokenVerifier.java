package com.hozgan.smartpay.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Dependency-free RS256 JWT verifier for the edge perimeter.
 *
 * <p>Accepts only the exact {@code RS256} algorithm, verifies the compact signature over
 * {@code header.payload} with {@code SHA256withRSA}, and enforces {@code exp} (with a small
 * configurable clock skew), optional {@code nbf}, optional configured {@code iss}, and the
 * mandatory {@code sub} / {@code tenant_id} claims.
 */
@Slf4j
public class JwtTokenVerifier {

    private static final String RS256 = "RS256";
    private static final String BEARER_PREFIX = "Bearer ";

    private final PublicKey publicKey;
    private final String expectedIssuer;
    private final long clockSkewSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtTokenVerifier(String publicKeyPem, String expectedIssuer, long clockSkewSeconds) {
        Objects.requireNonNull(publicKeyPem, "JWT public key must be configured");
        this.publicKey = parsePublicKey(resolveKeyMaterial(publicKeyPem));
        this.expectedIssuer = expectedIssuer == null ? "" : expectedIssuer;
        this.clockSkewSeconds = clockSkewSeconds;
    }

    /**
     * Verifies the {@code Authorization: Bearer <token>} header value and returns its claims.
     *
     * @throws JwtVerificationException on any structural, signature, or claim failure.
     */
    public JwtPrincipal verifyAuthorization(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new JwtVerificationException("Missing or malformed Authorization header");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return verify(token);
    }

    public JwtPrincipal verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtVerificationException("JWT must have three dot-separated segments");
        }
        String headerJson = decodeSegment(parts[0]);
        String payloadJson = decodeSegment(parts[1]);

        String algorithm = readAlgorithm(headerJson);
        if (!RS256.equals(algorithm)) {
            throw new JwtVerificationException("Only RS256 signed tokens are accepted, got: " + algorithm);
        }

        verifySignature(parts);

        JsonNode claims = readJson(payloadJson);
        return extractClaims(claims);
    }

    private String readAlgorithm(String headerJson) {
        try {
            return objectMapper.readTree(headerJson).path("alg").asText();
        } catch (JacksonException e) {
            throw new JwtVerificationException("JWT header is not valid JSON");
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new JwtVerificationException("JWT payload is not valid JSON");
        }
    }

    private void verifySignature(String[] parts) {
        byte[] signedData = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] signature;
        try {
            signature = decodeUrlSegment(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtVerificationException("JWT signature segment is not valid base64url");
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signedData);
            if (!verifier.verify(signature)) {
                throw new JwtVerificationException("JWT signature verification failed");
            }
        } catch (JwtVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtVerificationException("JWT signature could not be verified");
        }
    }

    private JwtPrincipal extractClaims(JsonNode claims) {
        Instant now = Instant.now();
        String subject = claims.path("sub").asText();
        String tenantId = claims.path("tenant_id").asText();
        if (subject.isBlank() || tenantId.isBlank()) {
            throw new JwtVerificationException("JWT must carry non-blank sub and tenant_id claims");
        }

        JsonNode expNode = claims.get("exp");
        if (expNode == null || !expNode.isNumber()) {
            throw new JwtVerificationException("JWT is missing the numeric exp claim");
        }
        Instant expiresAt = Instant.ofEpochSecond(expNode.asLong());
        if (expiresAt.isBefore(now.minusSeconds(clockSkewSeconds))) {
            throw new JwtVerificationException("JWT has expired");
        }

        JsonNode nbfNode = claims.get("nbf");
        if (nbfNode != null && nbfNode.isNumber()
                && Instant.ofEpochSecond(nbfNode.asLong()).isAfter(now.plusSeconds(clockSkewSeconds))) {
            throw new JwtVerificationException("JWT is not yet valid (nbf)");
        }

        String issuer = claims.path("iss").asText();
        if (!expectedIssuer.isEmpty() && !expectedIssuer.equals(issuer)) {
            throw new JwtVerificationException("JWT issuer mismatch");
        }

        List<String> roles = new ArrayList<>();
        JsonNode rolesNode = claims.get("roles");
        if (rolesNode != null && rolesNode.isArray()) {
            rolesNode.forEach(role -> roles.add(role.asText()));
        }
        return new JwtPrincipal(subject, tenantId, List.copyOf(roles));
    }

    private String decodeSegment(String segment) {
        try {
            return new String(decodeUrlSegment(segment), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new JwtVerificationException("JWT segment is not valid base64url");
        }
    }

    /** {@code java.util.Base64} URL decoder requires padding; JWT segments are unpadded per RFC 7515. */
    private byte[] decodeUrlSegment(String segment) {
        int remainder = segment.length() % 4;
        String padded = remainder == 0 ? segment : segment + "=".repeat(4 - remainder);
        return Base64.getUrlDecoder().decode(padded);
    }

    private String resolveKeyMaterial(String configured) {
        String value = configured.trim();
        if (value.contains("-----BEGIN")) {
            return value;
        }
        if (value.startsWith("classpath:")) {
            return readResource(new ClassPathResource(value.substring("classpath:".length())));
        }
        if (value.startsWith("file:")) {
            return readResource(new FileSystemResource(value.substring("file:".length())));
        }
        // Raw single-line PEM (whitespace stripped by shell/env) — re-wrap for the parser.
        return "-----BEGIN PUBLIC KEY-----\n" + value + "\n-----END PUBLIC KEY-----";
    }

    private String readResource(Resource resource) {
        try {
            if (!resource.exists()) {
                throw new JwtVerificationException("JWT public key resource not found: " + resource.getDescription());
            }
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JwtVerificationException("JWT public key resource could not be read");
        }
    }

    private PublicKey parsePublicKey(String pem) {
        String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new JwtVerificationException("Configured JWT public key is not a valid RSA PEM key");
        }
    }

    /** Raised for every rejected token; mapped to HTTP 401 by the authentication filter. */
    public static final class JwtVerificationException extends RuntimeException {
        public JwtVerificationException(String message) {
            super(message);
        }
    }
}
