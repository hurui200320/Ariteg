
rootProject.name = "Ariteg"
include("ariteg-core")
include("ariteg-minio")
include("ariteg-cmd")

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    versionCatalogs {
        create("libs") {
            library("clikt", "com.github.ajalt.clikt:clikt:3.5.0")
            library("minio-sdk", "io.minio:minio:8.4.3")
            library("bc-prov", "org.bouncycastle:bcprov-jdk18on:1.71")
            library("multihash-java", "com.github.multiformats:java-multihash:v1.3.0")
            library("jackson-kotlin", "com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3")
            library("commons-io", "commons-io:commons-io:2.11.0")
            library("nashorn", "org.openjdk.nashorn:nashorn-core:15.4")
        }
    }
}
