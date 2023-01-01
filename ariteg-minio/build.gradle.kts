plugins {
    kotlin("jvm")
}

group = "info.skyblond.ariteg"
version = VersionUtils.getVersion()
description = "Minio storage extension for ariteg"

dependencies {
    // minio sdk, export this dep
    api(libs.minio.sdk)
    // ariteg-core
    implementation(project(":ariteg-core"))
}