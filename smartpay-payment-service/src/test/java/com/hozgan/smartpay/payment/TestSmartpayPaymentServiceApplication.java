package com.hozgan.smartpay.payment;

import org.springframework.boot.SpringApplication;

public class TestSmartpayPaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartpayPaymentServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
