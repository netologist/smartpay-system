package com.hozgan.smartpay.common.model;

import com.hozgan.smartpay.common.converter.AccountIdConverter;
import com.hozgan.smartpay.common.converter.MoneyPenceConverter;
import com.hozgan.smartpay.common.converter.MoneyStringConverter;
import com.hozgan.smartpay.common.converter.TenantIdConverter;
import com.hozgan.smartpay.common.event.EpodVerifiedEvent;
import com.hozgan.smartpay.common.event.InvoiceIssuedEvent;
import com.hozgan.smartpay.common.event.OutboxEvent;
import com.hozgan.smartpay.common.exception.AccountNotFoundException;
import com.hozgan.smartpay.common.exception.InsufficientFundsException;
import com.hozgan.smartpay.common.exception.SmartpayDomainException;
import com.hozgan.smartpay.common.exception.UnbalancedJournalTransactionException;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.PaymentId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.common.event.PaymentInitiatedEvent;
import com.hozgan.smartpay.common.model.id.EndToEndId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Domain Models, IDs, Exceptions, and Converters Tests")
class DomainModelsTest {

    @Nested
    @DisplayName("Strongly-Typed Entity IDs")
    class EntityIdTests {

        @Test
        @DisplayName("UUID-based IDs generate valid UUIDv7 and validate null inputs")
        void uuidBasedIdsWork() {
            AccountId accountId = AccountId.generate();
            assertThat(accountId.value().version()).isEqualTo(7);
            assertThat(accountId.asString()).isEqualTo(accountId.value().toString());

            ShipperId shipperId = ShipperId.generate();
            CarrierId carrierId = CarrierId.generate();
            InvoiceId invoiceId = InvoiceId.generate();
            PaymentId paymentId = PaymentId.generate();

            assertThat(shipperId.value().version()).isEqualTo(7);
            assertThat(carrierId.value().version()).isEqualTo(7);
            assertThat(invoiceId.value().version()).isEqualTo(7);
            assertThat(paymentId.value().version()).isEqualTo(7);
            assertThat(paymentId.asString()).isEqualTo(paymentId.value().toString());
            assertThatThrownBy(() -> new AccountId(null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> AccountId.of((String) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("String-based IDs validate null and blank inputs")
        void stringBasedIdsWork() {
            TenantId tenantId = TenantId.of("tenant-uk-01");
            assertThat(tenantId.value()).isEqualTo("tenant-uk-01");

            assertThatThrownBy(() -> TenantId.of(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be blank");

            assertThatThrownBy(() -> TenantId.of(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Value Objects: GeoLocation, SignatureHash, AccountBalance, Pricing")
    class ValueObjectTests {

        @Test
        @DisplayName("GeoLocation validates valid coordinates and rejects out-of-range bounds")
        void geoLocationValidation() {
            GeoLocation london = GeoLocation.of(51.5074, -0.1278);
            assertThat(london.latitude()).isEqualByComparingTo("51.5074");
            assertThat(london.longitude()).isEqualByComparingTo("-0.1278");

            assertThatThrownBy(() -> GeoLocation.of(91.0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("latitude must be between -90 and 90");

            assertThatThrownBy(() -> GeoLocation.of(0.0, 185.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("longitude must be between -180 and 180");
        }

        @Test
        @DisplayName("SignatureHash validates 64-char hex sha256 string")
        void signatureHashValidation() {
            String validHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            SignatureHash hash = SignatureHash.of(validHash);
            assertThat(hash.value()).isEqualTo(validHash);

            assertThatThrownBy(() -> SignatureHash.of("invalid_short_hash"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("64-character hex");
        }

        @Test
        @DisplayName("AccountBalance availableBalance and canCover behavior")
        void accountBalanceCalculations() {
            AccountId accountId = AccountId.generate();
            Money cleared = Money.ofGBP("1000.00");
            Money hold = Money.ofGBP("250.00");

            AccountBalance balance = new AccountBalance(accountId, cleared, hold, 1L);
            assertThat(balance.availableBalance()).isEqualTo(Money.ofGBP("750.00"));
            assertThat(balance.canCover(Money.ofGBP("500.00"))).isTrue();
            assertThat(balance.canCover(Money.ofGBP("750.00"))).isTrue();
            assertThat(balance.canCover(Money.ofGBP("750.01"))).isFalse();
        }

        @Test
        @DisplayName("InvoicePricing validates that total equals base + fuel + vat")
        void invoicePricingValidation() {
            Money base = Money.ofGBP("500.00");
            Money fuel = Money.ofGBP("50.00");
            Money vat = Money.ofGBP("110.00");

            InvoicePricing pricing = InvoicePricing.calculate(base, fuel, vat);
            assertThat(pricing.totalAmount()).isEqualTo(Money.ofGBP("660.00"));

            assertThatThrownBy(() -> new InvoicePricing(base, fuel, vat, Money.ofGBP("700.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match sum");
        }

        @Test
        @DisplayName("Enums: EntryType opposite and VehicleType fromDbCode")
        void enumBehaviors() {
            assertThat(EntryType.DEBIT.opposite()).isEqualTo(EntryType.CREDIT);
            assertThat(EntryType.CREDIT.opposite()).isEqualTo(EntryType.DEBIT);

            assertThat(VehicleType.fromDbCode("7_5T")).isEqualTo(VehicleType.SEVEN_POINT_FIVE_TONNE);
            assertThat(VehicleType.fromDbCode("VAN")).isEqualTo(VehicleType.VAN);
            assertThat(VehicleType.fromDbCode("ARTIC")).isEqualTo(VehicleType.ARTIC);
        }
    }

    @Nested
    @DisplayName("JPA Converters Tests")
    class ConverterTests {

        @Test
        @DisplayName("MoneyPenceConverter converts Money to/from Long pence")
        void moneyPenceConverter() {
            MoneyPenceConverter converter = new MoneyPenceConverter();

            Long dbPence = converter.convertToDatabaseColumn(Money.ofGBP("12.50"));
            assertThat(dbPence).isEqualTo(1250L);

            Money entityMoney = converter.convertToEntityAttribute(1250L);
            assertThat(entityMoney).isEqualTo(Money.ofGBP("12.50"));

            assertThat(converter.convertToDatabaseColumn(null)).isNull();
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("MoneyStringConverter converts Money to/from formatted String")
        void moneyStringConverter() {
            MoneyStringConverter converter = new MoneyStringConverter();

            String dbStr = converter.convertToDatabaseColumn(Money.ofGBP("99.95"));
            assertThat(dbStr).isEqualTo("GBP 99.95");

            Money parsed = converter.convertToEntityAttribute("GBP 99.95");
            assertThat(parsed).isEqualTo(Money.ofGBP("99.95"));

            assertThat(converter.convertToDatabaseColumn(null)).isNull();
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("AccountIdConverter and TenantIdConverter auto-convert")
        void idConverters() {
            AccountIdConverter accConverter = new AccountIdConverter();
            UUID rawUuid = UUID.randomUUID();
            AccountId accId = AccountId.of(rawUuid);

            assertThat(accConverter.convertToDatabaseColumn(accId)).isEqualTo(rawUuid);
            assertThat(accConverter.convertToEntityAttribute(rawUuid)).isEqualTo(accId);

            TenantIdConverter tenantConverter = new TenantIdConverter();
            TenantId tenantId = TenantId.of("T-123");
            assertThat(tenantConverter.convertToDatabaseColumn(tenantId)).isEqualTo("T-123");
            assertThat(tenantConverter.convertToEntityAttribute("T-123")).isEqualTo(tenantId);
        }
    }

    @Nested
    @DisplayName("Domain Events & Outbox Tests")
    class EventTests {

        @Test
        @DisplayName("DomainEvent creation and OutboxEvent mapping")
        void outboxEventCreation() {
            LoadId loadId = LoadId.of("LOAD-UK-01");
            CarrierId carrierId = CarrierId.generate();
            EpodVerifiedEvent event = EpodVerifiedEvent.of(
                    loadId, carrierId, Instant.now(), GeoLocation.of(51.5, -0.1)
            );

            assertThat(event.eventType()).isEqualTo("EPOD_VERIFIED");
            assertThat(event.aggregateId()).isEqualTo("LOAD-UK-01");

            OutboxEvent outbox = OutboxEvent.from(event, "LOAD", "{\"status\":\"DELIVERED\"}");
            assertThat(outbox.isProcessed()).isFalse();
            assertThat(outbox.eventType()).isEqualTo("EPOD_VERIFIED");

            OutboxEvent processed = outbox.markProcessed();
            assertThat(processed.isProcessed()).isTrue();
            assertThat(processed.processedAt()).isNotNull();
        }

        @Test
        @DisplayName("PaymentInitiatedEvent creation and aggregateId contract")
        void paymentInitiatedEventContract() {
            PaymentId paymentId = PaymentId.generate();
            PaymentInitiatedEvent event = PaymentInitiatedEvent.of(
                    paymentId,
                    TenantId.of("T-1"),
                    AccountId.generate(),
                    AccountId.generate(),
                    Money.ofGBP("975.00"),
                    EndToEndId.of("E2E-123"),
                    "HOLD-1",
                    "FASTER_PAYMENTS",
                    "REF-1"
            );
            assertThat(event.eventType()).isEqualTo("PAYMENT_INITIATED");
            assertThat(event.aggregateId()).isEqualTo(paymentId.toString());
            assertThat(event.eventId()).isNotNull();
            assertThat(event.occurredAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Sealed Domain Exception Pattern Matching (Java 25)")
    class ExceptionPatternMatchingTests {

        @Test
        @DisplayName("Java 25 switch pattern matching over sealed SmartpayDomainException")
        void sealedExceptionPatternMatching() {
            AccountId acc = AccountId.generate();
            SmartpayDomainException ex = new InsufficientFundsException(acc, Money.ofGBP("100"), Money.ofGBP("50"));

            String category = switch (ex) {
                case InsufficientFundsException ife -> "INSUFFICIENT_FUNDS: " + ife.accountId();
                case AccountNotFoundException anfe -> "NOT_FOUND: " + anfe.accountId();
                case UnbalancedJournalTransactionException ujte -> "UNBALANCED_JOURNAL";
                default -> "OTHER_DOMAIN_ERROR";
            };

            assertThat(category).startsWith("INSUFFICIENT_FUNDS: ");
            assertThat(ex.errorCode()).isEqualTo("ERR_INSUFFICIENT_FUNDS");
            assertThat(ex.timestamp()).isNotNull();
        }
    }
}
