package com.hospital.scheduler.testsupport;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Stream;

/**
 * Shared MySQL 8 Testcontainers singleton for the entire test JVM.
 *
 * <p>Uses {@link ApplicationContextInitializer} to start the container
 * <em>before</em> Spring creates the DataSource, avoiding the timing
 * problem of {@code @PostConstruct}. Properties are injected directly
 * into the Spring Environment, overriding {@code application-test.yml}.
 *
 * <p>Usage — any {@code @SpringBootTest} that needs the real DB:
 * <pre>{@code
 * @SpringBootTest
 * @ActiveProfiles("test")
 * @ContextConfiguration(initializers = TestcontainerConfig.class)
 * class MyIntegrationTest { … }
 * }</pre>
 *
 * <p>ponytail: migrate to {@code @ServiceConnection} once the project
 * upgrades to a Spring Boot version where it is stable alongside
 * {@code @Transactional} rollback in integration tests.
 */
public class TestcontainerConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static volatile MySQLContainer<?> CONTAINER;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (CONTAINER == null || !CONTAINER.isRunning()) {
            CONTAINER = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("test_db")
                    .withUsername("testuser")
                    .withPassword("testpass")
                    .withReuse(false); // force fresh per-run so CI is hermetic
            Startables.deepStart(Stream.of(CONTAINER)).join();
        }

        String jdbcUrl = "jdbc:mysql://" + CONTAINER.getHost() + ":"
                + CONTAINER.getMappedPort(3306) + "/" + CONTAINER.getDatabaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";

        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "spring.datasource.url=" + jdbcUrl,
                "spring.datasource.username=" + CONTAINER.getUsername(),
                "spring.datasource.password=" + CONTAINER.getPassword(),
                "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect"
        );
    }

    /** Visible for manual inspection in debug mode. */
    public static MySQLContainer<?> container() {
        return CONTAINER;
    }
}
