group = "info.skyblond.ariteg"
version = rootProject.version as String
description = "Storage using normal file system."


dependencies {
    implementation(project(":storage-core"))
    // commons-io
    testImplementation(libs.commons.io)
}
