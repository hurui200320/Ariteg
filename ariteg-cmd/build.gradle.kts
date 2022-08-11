plugins {
    kotlin("jvm")
    application
}

group = "info.skyblond.ariteg"
version = VersionUtils.getVersion()
description = "CLI for ariteg"


application {
    mainClass.set("info.skyblond.ariteg.cmd.MainCommandKt")
    executableDir = ""
}

dependencies {
    // clikt
    implementation(libs.clikt)
    // Mozilla Rhino, running js on JVM
    implementation(libs.mozilla.rhino)
    // ariteg-core
    implementation(project(":ariteg-core"))
    // ariteg-minio
    implementation(project(":ariteg-minio"))
}
