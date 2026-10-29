import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin business rules shared by the app: no Android dependencies, fast JVM tests.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Java 17 bytecode, built with any JDK 17 or newer (same target as the app module).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
