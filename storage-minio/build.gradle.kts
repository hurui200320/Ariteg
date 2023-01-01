group = "info.skyblond.ariteg"
version = rootProject.version as String
description = "Storage using minio"

dependencies {
    // ariteg-core
    implementation(project(":storage-core"))
    // minio sdk, export this dep
    api("io.minio:minio:8.4.6")
}
