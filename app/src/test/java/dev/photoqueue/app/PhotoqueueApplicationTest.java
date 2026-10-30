package dev.photoqueue.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Boots the full context against a real Postgres and checks startup migrated the queue. */
@SpringBootTest
@Import(PhotoqueueApplicationTest.Containers.class)
class PhotoqueueApplicationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired
    JdbcClient jdbc;

    @Test
    void startupMigratesQueueSchema() {
        long jobs = jdbc.sql("select count(*) from jobqueue.jobs").query(Long.class).single();
        assertThat(jobs).isZero();
    }
}
