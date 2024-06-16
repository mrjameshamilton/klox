import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
    application
    jacoco
}

group = "eu.jameshamilton"
version = "2.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.guardsquare:proguard-core:9.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
    implementation("org.apache.commons:commons-text:1.10.0")
    compileOnly("org.jetbrains:annotations:22.0.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.0")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
    testImplementation("io.kotest:kotest-property-jvm:5.9.1")
    testImplementation("io.kotest:kotest-framework-datatest:5.9.1")
    testImplementation("io.mockk:mockk:1.12.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions.freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
}

tasks.withType<Test> {
    minHeapSize = "512m"
    maxHeapSize = "2048m"
}

application {
    mainClass.set("eu.jameshamilton.klox.MainKt")
}

ktlint {
    enableExperimentalRules.set(true)
    disabledRules.set(setOf("no-wildcard-imports", "experimental:argument-list-wrapping"))
}

tasks.register<Jar>("fatJar") {
    archiveFileName.set("klox.jar")

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "eu.jameshamilton.klox.MainKt"
        attributes["Implementation-Version"] = project.version
    }
}

tasks.register<Copy>("copyJar") {
    from(tasks.named("fatJar"))
    into("$rootDir/lib")
}

tasks.named("build") {
    finalizedBy(":copyJar")
}

tasks.named("clean") {
    doFirst {
        File("$rootDir/lib/klox.jar").delete()
    }
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        csv.required.set(true)
    }
}
