plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // Single source of truth for the Spring Boot version: the app plugin and the
    // dependency BOM used by every module (including the Spring-free queue).
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.1.2")
}
