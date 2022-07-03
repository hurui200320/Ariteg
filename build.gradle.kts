import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
}

group = "info.skyblond.ariteg"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.71")
    implementation("com.github.multiformats:java-multihash:v1.3.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3")
    implementation("commons-io:commons-io:2.11.0")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")
    implementation("ch.qos.logback:logback-classic:1.2.11")

    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:4.6.1")
}

tasks.test {
    useJUnitPlatform()
    minHeapSize = "512m"
    minHeapSize = "512m"
    jvmArgs = listOf("-Xmx512m", "-Xms512m")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
