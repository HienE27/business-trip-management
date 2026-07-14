package com.hospital.scheduler.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Shared MySQL 8 Testcontainers singleton for the entire test JVM.
 *
 * <p>Only one container is created regardless of how many
 * {@code @SpringBootTest} contexts load. The URL and credentials are
 * written into system properties so {@code spring.datasource.url} in
 * {@code application-test.yml} (which uses the {@code jdbc:tc:mysql:///} form)
 * resolves to the same container without requiring each test class to
 * manage its own lifecycle.
 *
 * <p>Usage — any {@code @SpringBootTest} that needs the real DB:
 * <pre>{@code
 * @SpringBootTest
 * @ActiveProfiles("test")
 * class MyIntegrationTest { … }
 * }</pre>
 *
 * <p>ponytail: remove once {@code @Testcontainers} per-class lifecycle is
 * preferred; this avoids the per-JVM container reuse anti-pattern when the
 * build runs in a forking JVM per test class.
 */
@TestConfiguration
public class TestcontainerConfig {

    private static MySQLContainer<?> CONTAINER;

    @PostConstruct
    void startContainer() {
        if (CONTAINER == null || !CONTAINER.isRunning()) {
            CONTAINER = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("test_db")
                    .withUsername("testuser")
                    .withPassword("testpass")
                    .withReuse(false); // force fresh per-run so CI is hermetic
            CONTAINER.start();
        }

        // Expose container connection details as system properties consumed
        // by the JDBC URL (e.g. spring.datasource.url via @Value or system prop).
        // This also lets plain @DataJpaTest slices connect when they use the
        // same profile without needing the jdbc:tc: protocol.
        System.setProperty("TEST_DB_HOST", CONTAINER.getHost());
        System.setProperty("TEST_DB_PORT", String.valueOf(CONTAINER.getMappedPort(3306)));
        System.setProperty("TEST_DB_NAME", CONTAINER.getDatabaseName());
        System.setProperty("TEST_DB_USER", CONTAINER.getUsername());
        System.setProperty("TEST_DB_PASS", CONTAINER.getPassword());

        // Canonical override — application-test.yml uses this exact key
        System.setProperty("spring.datasource.url",
                "jdbc:mysql://" + CONTAINER.getHost() + ":"
                        + CONTAINER.getMappedPort(3306) + "/" + CONTAINER.getDatabaseName()
                        + "?useSSL=false&allowPublicKeyRetrieval=true"
                        + "&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8");
        System.setProperty("spring.datasource.username", CONTAINER.getUsername());
        System.setProperty("spring.datasource.password", CONTAINER.getPassword());
        System.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.MySQLDialect");
    }

    @PreDestroy
    void stopContainer() {
        if (CONTAINER != null && CONTAINER.isRunning()) {
            CONTAINER.stop();
        }
    }

    /** Visible for manual inspection in debug mode. */
    public static MySQLContainer<?> container() {
        return CONTAINER;
    }
}
