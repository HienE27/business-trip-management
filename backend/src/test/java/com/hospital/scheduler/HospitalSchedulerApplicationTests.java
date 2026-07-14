package com.hospital.scheduler;

import com.hospital.scheduler.testsupport.TestcontainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context integration smoke — validates that every auto-configured bean
 * wires successfully against a real MySQL 8 container (via Testcontainers).
 *
 * <p>Disabled by default in CI (which uses the MySQL service container); enable
 * by running {@code mvn test -Dspring.profiles.active=test} locally or adding
 * {@code -Dskip.integration=false}.
 *
 * <p>ponytail: exclude this class from the standard {@code mvn test} run and
 * gate it behind a separate profile once the suite grows beyond 30 integration
 * tests. Add {@code <exclude>HospitalSchedulerApplicationTests.class</exclude>}
 * to the surefire plugin and run it as {@code mvn verify -Pit}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestcontainerConfig.class)
@DisplayName("Application context smoke (Testcontainers MySQL)")
class HospitalSchedulerApplicationTests {

    @Test
    void contextLoads() {
        // If this test passes, the Spring context started without any bean
        // wiring failures, the datasource connected, and JPA schema was created.
        // Actual assertions belong in domain-specific integration tests; this
        // one just verifies the app doesn't crash on startup.
        assertThat(1).isEqualTo(1);
    }
}

