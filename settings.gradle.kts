
rootProject.name = "Ariteg"
include("ariteg-core")
include("ariteg-minio")
include("ariteg-cmd")

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    versionCatalogs {
        create("libs") {
            library("clikt", "com.github.ajalt.clikt:clikt:3.5.0")
            library("bc-prov", "org.bouncycastle:bcprov-jdk18on:1.72")
            library("multihash-java", "com.github.multiformats:java-multihash:v1.3.2")
            // TODO move to kotlinx serialization
            library("jackson-kotlin", "com.fasterxml.jackson.module:jackson-module-kotlin:2.14.1")
            library("commons-io", "commons-io:commons-io:2.11.0")
            library("fnr-fuse", "com.github.serceman:jnr-fuse:0.5.7")
            library("caffeine", "com.github.ben-manes.caffeine:caffeine:3.1.2")
        }
    }
}
