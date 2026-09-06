package com.hozgan.smartpay.payout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = {"com.hozgan.smartpay.payout", "com.hozgan.smartpay.common"},
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
        }
)
public class SmartpayPayoutWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartpayPayoutWorkerApplication.class, args);
    }
}
