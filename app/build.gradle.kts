
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.kg.museumly"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kg.museumly"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(project(":domain"))
    // No code here imports :data. Hilt needs it on the classpath to find the bindings.
    implementation(project(":data"))
    implementation(project(":presentation"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.coil)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // AdMob
    //implementation(libs.play.services.ads)
}