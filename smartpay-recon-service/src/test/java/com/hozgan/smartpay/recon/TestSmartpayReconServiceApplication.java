package com.hozgan.smartpay.recon;

import org.springframework.boot.SpringApplication;

public class TestSmartpayReconServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartpayReconServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
