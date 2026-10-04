import org.gradle.kotlin.dsl.`kotlin-dsl`

plugins {
    /**
     * kotlin-dsl means this module contains Gradle build code written in Kotlin.
     * adds kotlin compiler.
     */
    `kotlin-dsl`
}
/**
 * compileOnly: the class needs Android's types to compile, but when it runs,
 * the Android plugin is already loaded by your main build. compileOnly means
 * "let me see it, don't bring a second copy". */
dependencies {
    compileOnly(libs.android.gradlePlugin)
}
/**
 * register: this is the link between the id you'll type in a build file and
 * the class that runs. "androidLibrary" is only a label for this entry.
 */
gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "museumly.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "museumly.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}