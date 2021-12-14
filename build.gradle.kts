import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    id("org.jlleitschuh.gradle.ktlint") version "10.1.0"
    application
    jacoco
}

group = "eu.jameshamilton"
version = "2.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.guardsquare:proguard-core:8.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.3")
    implementation("org.apache.commons:commons-text:1.9")
    compileOnly("org.jetbrains:annotations:22.0.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test:1.6.10")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.0.2")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.0.2")
    testImplementation("io.kotest:kotest-property-jvm:5.0.2")
    testImplementation("io.kotest:kotest-framework-datatest:5.0.2")
    testImplementation("io.mockk:mockk:1.12.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
}

tasks.withType<Test> {
    minHeapSize = "512m"
    maxHeapSize = "2048m"
    jvmArgs = listOf("-XX:MaxPermSize=512m")
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
