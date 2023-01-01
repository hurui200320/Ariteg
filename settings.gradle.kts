
rootProject.name = "Ariteg"
include("ariteg-core")
include("ariteg-cmd")
include("storage-core")
include("storage-file")
include("storage-minio")

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    versionCatalogs {
        create("libs") {
            library("commons-io", "commons-io:commons-io:2.11.0")
        }
    }
}
