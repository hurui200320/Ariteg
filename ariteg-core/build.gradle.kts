plugins {
    kotlin("jvm")
}

group = "info.skyblond.ariteg"
version = VersionUtils.getVersion()
description = "The core implementation of ariteg"

dependencies {
    // crypto operations
    implementation(libs.bc.prov)
    // multihash
    implementation(libs.multihash.java)
    // json & yaml operation
    implementation(libs.jackson.kotlin)
    // commons-io
    implementation(libs.commons.io)
}
