package com.hozgan.smartpay.invoice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import org.junit.jupiter.api.Tag;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SmartpayInvoiceServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
