package dev.techmart.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Nền cho mỗi integration test: PostgreSQL + Redis thật trong Docker.
 *
 * <p><b>Container dùng chung cho cả suite</b> (khởi trong static block, không dùng
 * {@code @Container} của JUnit). Nếu mỗi test class khởi động lại container thì suite
 * chạy 8 phút và sẽ không ai chạy nó nữa - kể cả chính mình.
 *
 * <p><b>Không đặt {@code @Transactional} lên test class</b>: nó khiến mọi thread trong
 * concurrency test chạy chung một transaction, và test sẽ pass giả trong khi production
 * vẫn oversell. Xem docs/05-test-plan.md mục 2.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    // Testcontainers 2: PostgreSQLContainer chuyển sang package org.testcontainers.postgresql
    // và bỏ tham số generic tự tham chiếu.
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
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
