plugins {
    kotlin("jvm")
}

group = "info.skyblond.ariteg"
version = VersionUtils.getVersion()
description = "Minio storage extension for ariteg"

dependencies {
    // minio sdk, export this dep
    api("io.minio:minio:8.4.6")
    // ariteg-core
    implementation(project(":ariteg-core"))
}
