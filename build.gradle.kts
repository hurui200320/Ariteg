import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm") version "1.7.21"
    id("maven-publish")
}

group = "info.skyblond.ariteg"
version = "2.0.0"
description = "Root project of ariteg"

allprojects {
    apply {
        plugin("java")
        plugin("org.jetbrains.kotlin.jvm")
    }

    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }

    dependencies {
        // kotlin logging
        implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")
        // test
        testImplementation(kotlin("test"))
        testImplementation("org.mockito:mockito-core:4.10.0")
    }

    tasks.test {
        useJUnitPlatform()
        minHeapSize = "512m"
        minHeapSize = "512m"
        jvmArgs = listOf("-Xmx512m", "-Xms512m")
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}

subprojects {
    apply {
        plugin("maven-publish")
    }
    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/hurui200320/Ariteg")
                credentials {
                    username = System.getenv("USERNAME")
                    password = System.getenv("TOKEN")
                }
            }
        }

        publications {
            create<MavenPublication>("maven") {
                groupId = "info.skyblond.ariteg"
                version = rootProject.version as String
                artifactId = project.name
                from(components["java"])
                pom {
                    licenses {
                        license {
                            name.set("GNU Affero General Public License v3.0")
                            url.set("https://www.gnu.org/licenses/agpl-3.0.en.html")
                        }
                    }
                    developers {
                        developer {
                            id.set("hurui200320")
                            name.set("Rui Hu")
                            email.set("hurui200320@skyblond.info")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/hurui200320/Ariteg.git")
                        url.set("https://github.com/hurui200320/Ariteg")
                    }
                }
            }
        }
    }
}
