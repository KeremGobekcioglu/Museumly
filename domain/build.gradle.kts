plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    /**
     * core, not android: this module is pure Kotlin. The android artifact only adds
     * Dispatchers.Main, which domain never uses.
     * api, not implementation: Flow is in the public interfaces (ArtworkRepository),
     * so modules that depend on :domain must see it too.
     */
    api(libs.kotlinx.coroutines.core)
}