
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.30"
    id("org.jlleitschuh.gradle.ktlint") version "10.1.0"
    application
}

group = "eu.jameshamilton"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-api:2.14.1")
    implementation("org.apache.logging.log4j:log4j-core:2.14.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test:1.5.21")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:4.6.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.6.1")
    testImplementation("io.kotest:kotest-property-jvm:4.6.1")
    testImplementation("io.mockk:mockk:1.12.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("MainKt")
}

ktlint {
    enableExperimentalRules.set(true)
    disabledRules.set(setOf("no-wildcard-imports", "experimental:argument-list-wrapping"))
}
