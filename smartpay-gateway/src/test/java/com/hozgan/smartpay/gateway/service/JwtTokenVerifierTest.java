package com.hozgan.smartpay.gateway.service;

import com.hozgan.smartpay.gateway.TestJwtKeys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class JwtTokenVerifierTest {

    private static KeyPair KEY_PAIR;

    private JwtTokenVerifier verifier = new JwtTokenVerifier(
            TestJwtKeys.publicKeyPem(KEY_PAIR), "https://issuer.smartpay.internal", 30);

    @BeforeAll
    static void generateKey() {
        KEY_PAIR = TestJwtKeys.rsaKeyPair();
    }

    @Test
    void verify_validToken_returnsPrincipalClaims() {
        Instant expiry = Instant.now().plus(1, ChronoUnit.HOURS);
        String token = TestJwtKeys.token(KEY_PAIR, "user-42", "TENANT-UK-01",
                List.of("ROLE_CARRIER"), expiry, "https://issuer.smartpay.internal");

        JwtPrincipal principal = verifier.verify(token);

        assertThat(principal.subject()).isEqualTo("user-42");
        assertThat(principal.tenantId()).isEqualTo("TENANT-UK-01");
        assertThat(principal.roles()).containsExactly("ROLE_CARRIER");
    }

    @Test
    void verify_missingAuthorization_rejected() {
        assertThatThrownBy(() -> verifier.verifyAuthorization(null))
                .isInstanceOf(JwtTokenVerifier.JwtVerificationException.class);
        assertThatThrownBy(() -> verifier.verifyAuthorization("Basic abc"))
                .isInstanceOf(JwtTokenVerifier.JwtVerificationException.class);
    }

    @Test
    void verify_expiredToken_rejected() {
        String token = TestJwtKeys.token(KEY_PAIR, "user-42", "TENANT-UK-01",
                Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtTokenVerifier.JwtVerificationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verify_tamperedPayload_rejected() {
        String token = TestJwtKeys.token(KEY_PAIR, "user-42", "TENANT-UK-01",
                Instant.now().plus(1, ChronoUnit.HOURS));
        String[] parts = token.split("\\.");
        String payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"attacker\",\"tenant_id\":\"TENANT-EVIL\"}".getBytes());
        // Payload swapped but the signature still covers the original payload → must fail.
        String tampered = parts[0] + "." + payloadB64 + "." + parts[2];

        assertThatThrownBy(() -> verifier.verify(tampered))
                .isInstanceOf(JwtTokenVerifier.JwtVerificationException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void verify_wrongAlgorithm_rejected() {
        // A structurally valid token signed with the correct key but claiming alg HS256 must be refused.
        String token = TestJwtKeys.token(KEY_PAIR, "user-42", "TENANT-UK-01",
                Instant.now().plus(1, ChronoUnit.HOURS));
        String[] parts = token.split("\\.");
        String headerB64 = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String forged = headerB64 + "." + parts[1] + "." + parts[2];

        assertThatThrownBy(() -> verifier.verify(forged))
                .isInstanceOf(JwtTokenVerifier.JwtVerificationException.class)
                .hasMessageContaining("RS256");
    }

    @Test
    void verify_missingTenantClaim_rejected() {
        // Cryptographically valid token that simply omits tenant_id.
        java.util.Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("sub", "user-42");
        claims.put("exp", Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond());
        String token = TestJwtKeys.tokenWithClaims(KEY_PAIR, claims);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtTokenVerifier.JwtVerificationException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void verify_wrongIssuer_rejected() {
        String token = TestJwtKeys.token(KEY_PAIR, "user-42", "TENANT-UK-01",
                List.of(), Instant.now().plus(1, ChronoUnit.HOURS), "https://evil.example");

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtTokenVerifier.JwtVerificationException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void verify_tokenSignedByAnotherKey_rejected() {
        KeyPair other = TestJwtKeys.rsaKeyPair();
        String token = TestJwtKeys.token(other, "user-42", "TENANT-UK-01",
                Instant.now().plus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtTokenVerifier.JwtVerificationException.class);
    }
}
