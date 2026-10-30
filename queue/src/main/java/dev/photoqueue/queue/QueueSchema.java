package dev.photoqueue.queue;

import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * Owns the {@code jobqueue} Postgres schema. The queue keeps its own schema and
 * migration history so it never collides with the application's migrations.
 */
public final class QueueSchema {

    public static final String NAME = "jobqueue";

    private final DataSource dataSource;

    public QueueSchema(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Creates the schema if absent and applies pending migrations. Safe to call on every
     * startup and from several instances at once: Flyway serializes runs with an advisory lock.
     *
     * @return number of migrations applied
     */
    public int migrate() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(NAME)
                .locations("classpath:db/migration/" + NAME)
                .load()
                .migrate()
                .migrationsExecuted;
    }
}
