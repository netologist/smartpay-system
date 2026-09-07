package com.hozgan.smartpay.risk;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SmartpayRiskServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
