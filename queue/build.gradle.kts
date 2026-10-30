// Generic Postgres job queue. Plain Java + JDBC: no Spring, no photo code.
plugins {
    id("photoqueue.java-conventions")
    `java-library`
}

dependencies {
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.postgresql:postgresql")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
