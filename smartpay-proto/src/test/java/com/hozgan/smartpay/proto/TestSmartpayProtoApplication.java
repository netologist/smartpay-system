package com.hozgan.smartpay.proto;

import org.springframework.boot.SpringApplication;

public class TestSmartpayProtoApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartpayProtoApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
