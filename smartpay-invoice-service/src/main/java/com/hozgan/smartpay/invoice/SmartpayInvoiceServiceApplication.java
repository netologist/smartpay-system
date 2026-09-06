package com.hozgan.smartpay.invoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.hozgan.smartpay.invoice", "com.hozgan.smartpay.common"})
public class SmartpayInvoiceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartpayInvoiceServiceApplication.class, args);
	}

}
