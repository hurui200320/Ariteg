import kotlin.collections.listOf

plugins {
    kotlin("jvm")
    application
}

group = "info.skyblond.ariteg"
version = VersionUtils.getVersion()
description = "CLI for ariteg"


application {
    mainClass.set("info.skyblond.ariteg.cmd.MainCommandKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
    executableDir = ""
}

dependencies {
    // clikt
    implementation(libs.clikt)
    // oracle nashorn
    implementation(libs.nashorn)
    // commons-io
    implementation(libs.commons.io)
    // ariteg-core
    implementation(project(":ariteg-core"))
    // ariteg-minio
    implementation(project(":ariteg-minio"))
    // jnr-fuse
    implementation(libs.fnr.fuse)
    // caffeine
    implementation(libs.caffeine)
    // kotlin coroutines
    implementation(libs.kotlinx.coroutine)
}
