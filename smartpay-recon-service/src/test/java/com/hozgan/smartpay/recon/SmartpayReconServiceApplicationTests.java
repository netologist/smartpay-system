package com.hozgan.smartpay.recon;

import com.hozgan.smartpay.recon.web.ReconGrpcTestConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Tag("integration")
@Import({TestcontainersConfiguration.class, ReconGrpcTestConfig.class})
@SpringBootTest
class SmartpayReconServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
