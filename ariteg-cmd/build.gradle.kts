plugins {
    kotlin("jvm")
    application
}

group = "info.skyblond.ariteg"
version = rootProject.version as String
description = "CLI for ariteg"


application {
    mainClass.set("info.skyblond.ariteg.cmd.MainCommandKt")
//    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
    executableDir = ""
}

dependencies {
    implementation(project(":ariteg-core"))
    implementation(project(":storage-core"))
    implementation(project(":storage-file"))
    implementation(project(":storage-minio"))
    // clikt
    implementation("com.github.ajalt.clikt:clikt:3.5.0")
    // jnr-fuse
    implementation("com.github.serceman:jnr-fuse:0.5.7")
    // caffeine
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.2")
    // commons-io
    implementation(libs.commons.io)
}
