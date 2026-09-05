package com.hozgan.smartpay.invoice;

import org.springframework.boot.SpringApplication;

public class TestSmartpayInvoiceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartpayInvoiceServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
