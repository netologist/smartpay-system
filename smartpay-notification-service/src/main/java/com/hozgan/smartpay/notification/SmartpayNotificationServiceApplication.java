package com.hozgan.smartpay.notification;

import com.hozgan.smartpay.notification.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
@EnableConfigurationProperties(NotificationProperties.class)
public class SmartpayNotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartpayNotificationServiceApplication.class, args);
	}

}
