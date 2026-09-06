package com.hozgan.smartpay.payment.mapper;

import com.hozgan.smartpay.common.event.PaymentInitiatedEvent;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.model.id.EndToEndId;
import com.hozgan.smartpay.common.model.id.PaymentId;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.payment.dto.response.PaymentResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PaymentMapperTest {

    private final PaymentMapper mapper = Mappers.getMapper(PaymentMapper.class);

    @Test
    void toResponse_mapsAllFieldsCorrectly() {
        PaymentId paymentId = PaymentId.generate();
        TenantId tenantId = TenantId.of("TENANT-UK-01");
        AccountId debtor = AccountId.generate();
        AccountId creditor = AccountId.generate();
        Money amount = Money.ofGBP("975.00");
        EndToEndId endToEndId = EndToEndId.of("E2E-SMARTPAY-20260906-0001");
        String holdId = "HOLD-001";
        Instant now = Instant.now();

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                UUID.randomUUID(),
                paymentId,
                tenantId,
                debtor,
                creditor,
                amount,
                endToEndId,
                holdId,
                "FASTER_PAYMENTS",
                "REF-123",
                now
        );

        PaymentResponse response = mapper.toResponse(event);

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(paymentId.toString());
        assertThat(response.status()).isEqualTo("INITIATED");
        assertThat(response.amountInPence()).isEqualTo(97500L);
        assertThat(response.currency()).isEqualTo("GBP");
        assertThat(response.amount()).isEqualTo("975.00");
        assertThat(response.endToEndId()).isEqualTo(endToEndId.value());
        assertThat(response.debtorAccountId()).isEqualTo(debtor.toString());
        assertThat(response.creditorAccountId()).isEqualTo(creditor.toString());
        assertThat(response.createdAt()).isEqualTo(now);
    }
}
