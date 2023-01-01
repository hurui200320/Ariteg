plugins {
    kotlin("jvm")
    application
}

group = "info.skyblond.ariteg"
version = rootProject.version as String
description = "CLI for ariteg"


application {
    mainClass.set("info.skyblond.ariteg.cmd.MainCommandKt")
    // Windows' GB2312 seems not working with jnr-fuse
    // but forcing UTF-8 will give garbage output on console when working with Chinese
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
    executableDir = ""
}

dependencies {
    implementation(project(":ariteg-core"))
    implementation(project(":storage-core"))
    implementation(project(":storage-file"))
    implementation(project(":storage-minio"))
    // logback
    implementation("ch.qos.logback:logback-classic:1.4.5")
    // groovy scripting
    implementation("org.apache.groovy:groovy:4.0.7")
    // clikt
    implementation("com.github.ajalt.clikt:clikt:3.5.0")
    // jnr-fuse
    implementation("com.github.serceman:jnr-fuse:0.5.7")
    // caffeine
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.2")
    // commons-io
    implementation(libs.commons.io)
}
