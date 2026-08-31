package dev.techmart.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Nen cho moi integration test: PostgreSQL + Redis that trong Docker.
 *
 * <p><b>Container dung chung cho ca suite</b> (khoi trong static block, khong dung
 * {@code @Container} cua JUnit). Neu moi test class khoi dong lai container thi suite
 * chay 8 phut va se khong ai chay no nua - ke ca chinh minh.
 *
 * <p><b>Khong dat {@code @Transactional} len test class</b>: no khien moi thread trong
 * concurrency test chay chung mot transaction, va test se pass gia trong khi production
 * van oversell. Xem docs/05-test-plan.md muc 2.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("techmart_test")
                    .withUsername("techmart")
                    .withPassword("techmart")
                    .withReuse(true);

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
