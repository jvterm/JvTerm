plugins {
    kotlin("jvm")
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":terminal-core"))
    implementation(project(":terminal-integration"))
    implementation(project(":terminal-parser"))
    implementation(project(":terminal-render-api"))
    implementation(project(":terminal-render-cache"))
    implementation(project(":terminal-protocol"))
    implementation(project(":terminal-session"))
    implementation(project(":terminal-ui-swing"))

    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

kotlin {
    jvmToolchain(21)
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    benchmarkMode.set(listOf("thrpt"))
    timeUnit.set("ms")
    profilers.set(listOf("gc"))
}
