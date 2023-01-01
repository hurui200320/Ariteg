plugins {
    kotlin("jvm")
}

group = "info.skyblond.ariteg"
version = rootProject.version as String
description = "The core implementation of ariteg"

dependencies {
    implementation(project(":storage-core"))
    testImplementation(project(":storage-file"))
    // commons-io
    testImplementation(libs.commons.io)
}
