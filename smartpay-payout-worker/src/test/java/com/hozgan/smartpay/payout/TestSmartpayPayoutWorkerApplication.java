package com.hozgan.smartpay.payout;

import org.springframework.boot.SpringApplication;

public class TestSmartpayPayoutWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartpayPayoutWorkerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
