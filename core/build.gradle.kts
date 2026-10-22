// Pure Kotlin/JVM module: the scheduler, grader, queue, session (domain/), the vocabulary model and card
// builders (content/) and the logging interface (debug/). It has no Android on its classpath, so an
// `import android.*` here does not compile: the "domain stays framework-free" rule is enforced by the compiler.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    jacoco
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.test { finalizedBy(tasks.jacocoTestReport) }
tasks.jacocoTestReport { reports { xml.required.set(true) } }
