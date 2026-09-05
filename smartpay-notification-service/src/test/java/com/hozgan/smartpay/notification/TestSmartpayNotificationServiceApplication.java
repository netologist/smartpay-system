package com.hozgan.smartpay.notification;

import org.springframework.boot.SpringApplication;

public class TestSmartpayNotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartpayNotificationServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
