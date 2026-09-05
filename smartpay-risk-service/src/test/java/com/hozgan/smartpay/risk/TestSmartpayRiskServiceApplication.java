package com.hozgan.smartpay.risk;

import org.springframework.boot.SpringApplication;

public class TestSmartpayRiskServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartpayRiskServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
