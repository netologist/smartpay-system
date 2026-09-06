package com.hozgan.smartpay.payment;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SmartpayPaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
