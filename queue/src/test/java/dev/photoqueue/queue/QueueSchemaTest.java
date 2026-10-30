package dev.photoqueue.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.util.PSQLException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Applies the queue migrations to a real Postgres and checks the schema's invariants. */
@Testcontainers
class QueueSchemaTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static PGSimpleDataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        assertThat(new QueueSchema(dataSource).migrate()).isEqualTo(1);
    }

    @Test
    void migrateIsIdempotent() {
        assertThat(new QueueSchema(dataSource).migrate()).isZero();
    }

    @Test
    void acceptsReadyJob() throws SQLException {
        assertThat(insertJob("READY", 0, false)).isPositive();
    }

    @Test
    void acceptsBlockedJobWithPendingDeps() throws SQLException {
        assertThat(insertJob("BLOCKED", 2, false)).isPositive();
    }

    @Test
    void acceptsRunningJobWithLease() throws SQLException {
        assertThat(insertJob("RUNNING", 0, true)).isPositive();
    }

    @Test
    void rejectsUnknownStatus() {
        assertViolates("jobs_status_valid", () -> insertJob("FAILED", 0, false));
    }

    @Test
    void rejectsBlockedJobWithoutPendingDeps() {
        assertViolates("jobs_blocked_iff_pending_deps", () -> insertJob("BLOCKED", 0, false));
    }

    @Test
    void rejectsReadyJobWithPendingDeps() {
        assertViolates("jobs_blocked_iff_pending_deps", () -> insertJob("READY", 1, false));
    }

    @Test
    void rejectsRunningJobWithoutLease() {
        assertViolates("jobs_running_has_lease", () -> insertJob("RUNNING", 0, false));
    }

    @Test
    void rejectsSelfDependency() throws SQLException {
        long id = insertJob("READY", 0, false);
        assertViolates("job_dependencies_not_self", () -> execute(
                "insert into jobqueue.job_dependencies (depends_on_id, job_id) values (" + id + ", " + id + ")"));
    }

    private static long insertJob(String status, int pendingDeps, boolean leased) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement("""
                        insert into jobqueue.jobs
                            (queue, type, tenant_id, status, max_attempts, pending_deps, locked_by, locked_until)
                        values ('test', 'noop', 'tenant-1', ?, 3, ?, ?, ?)
                        returning id
                        """)) {
            insert.setString(1, status);
            insert.setInt(2, pendingDeps);
            insert.setString(3, leased ? "worker-1" : null);
            insert.setObject(4, leased ? OffsetDateTime.now().plusMinutes(5) : null, Types.TIMESTAMP_WITH_TIMEZONE);
            try (ResultSet rs = insert.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void assertViolates(String constraint, ThrowingCallable statement) {
        assertThatThrownBy(statement).isInstanceOfSatisfying(PSQLException.class,
                e -> assertThat(e.getServerErrorMessage().getConstraint()).isEqualTo(constraint));
    }
}
