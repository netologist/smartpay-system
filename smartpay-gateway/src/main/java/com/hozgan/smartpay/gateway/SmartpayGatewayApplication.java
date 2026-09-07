package com.hozgan.smartpay.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartpayGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartpayGatewayApplication.class, args);
	}

}
