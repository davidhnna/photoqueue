package dev.photoqueue.app.jobs;

import dev.photoqueue.queue.QueueSchema;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the Spring-free queue module into the application. */
@Configuration(proxyBeanMethods = false)
class QueueConfig {

    // Migrates during context startup, so queue tables exist before any bean that uses them.
    @Bean(initMethod = "migrate")
    QueueSchema queueSchema(DataSource dataSource) {
        return new QueueSchema(dataSource);
    }
}
