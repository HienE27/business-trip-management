package com.hospital.scheduler;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Disabled("Requires full Spring context with DB - run manually or with integration profile")
class HospitalSchedulerApplicationTests {

    @Test
    void contextLoads() {
    }
}
