package com.hozgan.smartpay.notification.service;

import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.notification.model.RecipientProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("RecipientResolver — Unit Tests")
class RecipientResolverTest {

    private DefaultRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultRecipientResolver();
    }

    @Test
    @DisplayName("Resolves default carrier profile when not explicitly registered")
    void shouldResolveDefaultCarrierProfile() {
        CarrierId carrierId = CarrierId.generate();
        RecipientProfile profile = resolver.resolveCarrier(carrierId);

        assertThat(profile).isNotNull();
        assertThat(profile.phone()).isEqualTo("+447700900123");
        assertThat(profile.email()).isEqualTo("carrier-ops@smartpay.io");
        assertThat(profile.webhookSecret()).isNotBlank();
    }

    @Test
    @DisplayName("Resolves custom carrier profile when registered")
    void shouldResolveCustomCarrierProfile() {
        CarrierId carrierId = CarrierId.generate();
        RecipientProfile custom = new RecipientProfile(
                "Acme Transport",
                "+447911123456",
                "ops@acmetransport.co.uk",
                "https://acmetransport.co.uk/webhook",
                "acme-secret"
        );

        resolver.registerCarrier(carrierId, custom);
        RecipientProfile resolved = resolver.resolveCarrier(carrierId);

        assertThat(resolved.name()).isEqualTo("Acme Transport");
        assertThat(resolved.phone()).isEqualTo("+447911123456");
        assertThat(resolved.email()).isEqualTo("ops@acmetransport.co.uk");
        assertThat(resolved.webhookUrl()).isEqualTo("https://acmetransport.co.uk/webhook");
    }

    @Test
    @DisplayName("Resolves shipper profile correctly")
    void shouldResolveShipperProfile() {
        ShipperId shipperId = ShipperId.generate();
        RecipientProfile profile = resolver.resolveShipper(shipperId);

        assertThat(profile).isNotNull();
        assertThat(profile.phone()).isEqualTo("+447700900456");
        assertThat(profile.email()).isEqualTo("shipper-invoicing@smartpay.io");
    }
}
