group = "info.skyblond.ariteg"
version = rootProject.version as String
description = "The essence part of the storage."


dependencies {
    // crypto operations
    implementation("org.bouncycastle:bcprov-jdk18on:1.72")
    // multihash
    implementation("com.github.multiformats:java-multihash:v1.3.2")
    // bencode
    implementation("com.dampcake:bencode:1.4")
    // kotlin coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}
