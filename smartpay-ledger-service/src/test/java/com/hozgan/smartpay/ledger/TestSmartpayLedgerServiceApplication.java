package com.hozgan.smartpay.ledger;

import org.springframework.boot.SpringApplication;

public class TestSmartpayLedgerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartpayLedgerServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
