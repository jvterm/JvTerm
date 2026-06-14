plugins {
    kotlin("jvm") version "2.4.0" apply false
    id("com.diffplug.spotless") version "8.6.0"
    id("org.jetbrains.dokka") version "2.0.0"
}

repositories {
    mavenCentral()
}

subprojects {
    repositories {
        mavenCentral()
    }

    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "org.jetbrains.dokka")

    plugins.withId("org.jetbrains.dokka") {
        if (file("Module.md").exists()) {
            extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
                dokkaPublications.configureEach {
                    includes.from("Module.md")
                }
            }
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint("1.3.1")
            licenseHeaderFile(rootProject.file("gradle/license-header.txt"))
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.3.1")
        }
    }
}


