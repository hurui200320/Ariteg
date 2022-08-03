import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
    application
}

group = "info.skyblond.ariteg"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("info.skyblond.ariteg.cmd.MainCommandKt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // crypto operations
    implementation("org.bouncycastle:bcprov-jdk18on:1.71")
    // multihash
    implementation("com.github.multiformats:java-multihash:v1.3.0")
    // json & yaml operation
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3")
    // commons-io
    implementation("commons-io:commons-io:2.11.0")
    // kotlin logging
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    // minio sdk
    implementation("io.minio:minio:8.4.3")
    // clikt
    implementation("com.github.ajalt.clikt:clikt:3.5.0")



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
