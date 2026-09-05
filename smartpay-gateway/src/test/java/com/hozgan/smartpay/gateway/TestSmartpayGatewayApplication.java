package com.hozgan.smartpay.gateway;

import org.springframework.boot.SpringApplication;

public class TestSmartpayGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartpayGatewayApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
